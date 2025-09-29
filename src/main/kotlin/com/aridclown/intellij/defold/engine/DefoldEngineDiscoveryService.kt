package com.aridclown.intellij.defold.engine

import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class DefoldEngineDiscoveryService(project: Project) {

    private val lock = Any()
    private val activeHandler = AtomicReference<OSProcessHandler?>()
    @Volatile
    private var info: EngineTargetInfo = EngineTargetInfo()

    fun attachToProcess(handler: OSProcessHandler) {
        synchronized(lock) {
            activeHandler.set(handler)
            info = EngineTargetInfo()
        }

        handler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                recordLogLine(event.text)
            }

            override fun processTerminated(event: ProcessEvent) {
                clearIfOwned(handler)
            }
        })
    }

    internal fun recordLogLine(rawLine: String) {
        val line = rawLine.trim()
        if (line.isEmpty()) return

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
        val snapshot = info
        val port = snapshot.servicePort ?: return null
        val address = snapshot.address ?: DEFAULT_ADDRESS
        return DefoldEngineEndpoint(
            address = address,
            port = port,
            logPort = snapshot.logPort,
            lastUpdatedMillis = snapshot.lastUpdatedMillis
        )
    }

    fun clear() {
        synchronized(lock) {
            info = EngineTargetInfo()
            activeHandler.set(null)
        }
    }

    private fun clearIfOwned(handler: OSProcessHandler) {
        synchronized(lock) {
            if (activeHandler.get() == handler) {
                info = EngineTargetInfo()
                activeHandler.set(null)
            }
        }
    }

    private fun updateInfo(modifier: (EngineTargetInfo) -> EngineTargetInfo) {
        synchronized(lock) {
            info = modifier(info)
        }
    }

    private data class EngineTargetInfo(
        val address: String? = null,
        val servicePort: Int? = null,
        val logPort: Int? = null,
        val lastUpdatedMillis: Long = 0L
    )

    companion object {
        private const val DEFAULT_ADDRESS = "127.0.0.1"
        private val LOG_PORT_REGEX = Regex("Log server started on port (\\d+)")
        private val SERVICE_PORT_REGEX = Regex("Engine service started on port (\\d+)")
        private val TARGET_ADDRESS_REGEX = Regex("Target listening with name: .* - ([^ ]+) - .*")
    }
}

data class DefoldEngineEndpoint(
    val address: String,
    val port: Int,
    val logPort: Int?,
    val lastUpdatedMillis: Long
)
