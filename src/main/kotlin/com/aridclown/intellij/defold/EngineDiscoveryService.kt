package com.aridclown.intellij.defold

import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

@Service(PROJECT)
class EngineDiscoveryService {

    companion object {
        private const val DEFAULT_ADDRESS = "127.0.0.1"
        private val LOG_PORT_REGEX = Regex("Log server started on port (\\d+)")
        private val SERVICE_PORT_REGEX = Regex("Engine service started on port (\\d+)")
        private val TARGET_ADDRESS_REGEX = Regex("Started on address ([^ ]+)")

        fun Project.getEngineDiscoveryService(): EngineDiscoveryService = service<EngineDiscoveryService>()
    }

    private val lock = Any()
    private val runningEngines = mutableListOf<RunningEngine>()
    private val engineInfoByHandler = mutableMapOf<OSProcessHandler, EngineTargetInfo>()

    fun attachToProcess(handler: OSProcessHandler, debugPort: Int?) {
        synchronized(lock) {
            runningEngines.removeAll { it.handler == handler }
            runningEngines.add(RunningEngine(handler, debugPort))
            engineInfoByHandler[handler] = EngineTargetInfo()
        }

        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                recordLogLine(handler, event.text)
            }

            override fun processTerminated(event: ProcessEvent) {
                clear(handler)
            }
        })
    }

    fun stopEnginesForPort(debugPort: Int?) {
        val handlers = synchronized(lock) {
            val (matching, remaining) = runningEngines.partition { entry ->
                debugPort == null || entry.debugPort == debugPort
            }
            runningEngines.clear()
            runningEngines.addAll(remaining)
            matching.forEach { engineInfoByHandler.remove(it.handler) }
            matching.map(RunningEngine::handler)
        }

        handlers.forEach { handler ->
            if (!handler.isProcessTerminating && !handler.isProcessTerminated) {
                handler.destroyProcess()
            }
        }
    }

    fun hasEngineForPort(debugPort: Int?): Boolean = synchronized(lock) {
        runningEngines.any { entry -> debugPort == null || entry.debugPort == debugPort }
    }

    fun recordLogLine(handler: OSProcessHandler, rawLine: String) {
        val line = rawLine.trim().ifEmpty { return }

        fun update(modifier: (EngineTargetInfo) -> EngineTargetInfo) {
            synchronized(lock) {
                val current = engineInfoByHandler[handler] ?: EngineTargetInfo()
                engineInfoByHandler[handler] = modifier(current)
            }
        }

        LOG_PORT_REGEX.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { port ->
            update { it.copy(logPort = port, lastUpdatedMillis = System.currentTimeMillis()) }
        }

        SERVICE_PORT_REGEX.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { port ->
            update { it.copy(servicePort = port, lastUpdatedMillis = System.currentTimeMillis()) }
        }

        TARGET_ADDRESS_REGEX.find(line)?.groupValues?.getOrNull(1)?.let { address ->
            update { current ->
                current.copy(address = address, lastUpdatedMillis = System.currentTimeMillis())
            }
        }
    }

    fun currentEndpoint(): EngineEndpoint? = currentEndpoints().firstOrNull()

    fun currentEndpoints(): List<EngineEndpoint> {
        val infos = synchronized(lock) { engineInfoByHandler.values.toList() }

        return infos.mapNotNull { info ->
            val port = info.servicePort ?: return@mapNotNull null
            EngineEndpoint(
                address = info.address ?: DEFAULT_ADDRESS,
                port = port,
                logPort = info.logPort,
                lastUpdatedMillis = info.lastUpdatedMillis
            )
        }
    }

    private fun clear(handler: OSProcessHandler) {
        synchronized(lock) {
            runningEngines.removeAll { it.handler == handler }
            engineInfoByHandler.remove(handler)
        }
    }

    private data class RunningEngine(
        val handler: OSProcessHandler,
        val debugPort: Int?
    )

    private data class EngineTargetInfo(
        val address: String? = null,
        val servicePort: Int? = null,
        val logPort: Int? = null,
        val lastUpdatedMillis: Long = 0L
    )
}

data class EngineEndpoint(
    val address: String,
    val port: Int,
    val logPort: Int?,
    val lastUpdatedMillis: Long
)
