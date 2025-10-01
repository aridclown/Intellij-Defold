package com.aridclown.intellij.defold.engine

import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.util.concurrent.atomic.AtomicReference

@Service(PROJECT)
class DefoldEngineDiscoveryService {

    companion object {
        private const val DEFAULT_ADDRESS = "127.0.0.1"
        private val LOG_PORT_REGEX = Regex("Log server started on port (\\d+)")
        private val SERVICE_PORT_REGEX = Regex("Engine service started on port (\\d+)")
        private val TARGET_ADDRESS_REGEX = Regex("Target listening with name: .* - ([^ ]+) - .*")

        fun Project.getEngineDiscoveryService(): DefoldEngineDiscoveryService =
            service<DefoldEngineDiscoveryService>()
    }

    private val lock = Any()
    private val activeHandler = AtomicReference<OSProcessHandler?>()

    @Volatile
    private var engineTargetInfo = EngineTargetInfo()

    fun attachToProcess(handler: OSProcessHandler) {
        synchronized(lock) {
            activeHandler.set(handler)
            engineTargetInfo = EngineTargetInfo()
        }

        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                recordLogLine(event.text)
            }

            override fun processTerminated(event: ProcessEvent) {
                clearIfOwned(handler)
            }
        })
    }

    fun stopActiveEngine() {
        val handler = synchronized(lock) {
            val current = activeHandler.getAndSet(null)
            if (current != null) {
                engineTargetInfo = EngineTargetInfo()
            }
            current
        } ?: return

        if (!handler.isProcessTerminating && !handler.isProcessTerminated) {
            handler.destroyProcess()
        }
    }

    internal fun recordLogLine(rawLine: String) {
        val line = rawLine.trim().ifEmpty { return }

        LOG_PORT_REGEX.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { port ->
            updateInfo { it.copy(logPort = port, lastUpdatedMillis = System.currentTimeMillis()) }
        }

        SERVICE_PORT_REGEX.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { port ->
            updateInfo { it.copy(servicePort = port, lastUpdatedMillis = System.currentTimeMillis()) }
        }

        TARGET_ADDRESS_REGEX.find(line)?.groupValues?.getOrNull(1)?.let { address ->
            updateInfo { current ->
                current.copy(address = address, lastUpdatedMillis = System.currentTimeMillis())
            }
        }
    }

    fun currentEndpoint(): DefoldEngineEndpoint? {
        return DefoldEngineEndpoint(
            address = engineTargetInfo.address ?: DEFAULT_ADDRESS,
            port = engineTargetInfo.servicePort ?: return null,
            logPort = engineTargetInfo.logPort,
            lastUpdatedMillis = engineTargetInfo.lastUpdatedMillis
        )
    }

    private fun clearIfOwned(handler: OSProcessHandler) {
        synchronized(lock) {
            if (activeHandler.get() == handler) {
                engineTargetInfo = EngineTargetInfo()
                activeHandler.set(null)
            }
        }
    }

    private fun updateInfo(modifier: (EngineTargetInfo) -> EngineTargetInfo) {
        synchronized(lock) {
            engineTargetInfo = modifier(engineTargetInfo)
        }
    }

    private data class EngineTargetInfo(
        val address: String? = null,
        val servicePort: Int? = null,
        val logPort: Int? = null,
        val lastUpdatedMillis: Long = 0L
    )
}

data class DefoldEngineEndpoint(
    val address: String,
    val port: Int,
    val logPort: Int?,
    val lastUpdatedMillis: Long
)
