package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.DEFAULT_MOBDEBUG_PORT
import com.aridclown.intellij.defold.DefoldConstants.INI_DEBUG_INIT_SCRIPT_VALUE
import com.aridclown.intellij.defold.EngineDiscoveryService.Companion.getEngineDiscoveryService
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.aridclown.intellij.defold.util.printError
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

/**
 * Handles launching the Defold engine after a successful build
 */
class EngineRunner(
    private val processExecutor: ProcessExecutor
) {

    fun launchEngine(
        runRequest: RunRequest,
        enginePath: Path
    ): OSProcessHandler? = with(runRequest) {
        runCatching {
            val workspace = project.basePath ?: error("Project has no base path")
            val command = GeneralCommandLine(enginePath.toAbsolutePath().pathString)
                .withWorkingDirectory(Path(workspace))
                .applyEnvironment(envData)

            if (enableDebugScript) {
                val port = debugPort ?: DEFAULT_MOBDEBUG_PORT
                command
                    .withParameters("--config=bootstrap.debug_init_script=$INI_DEBUG_INIT_SCRIPT_VALUE")
                    .withEnvironment("DM_SERVICE_PORT", serverPort?.toString() ?: "8001")
                    .withEnvironment("MOBDEBUG_PORT", port.toString())
            }

            processExecutor.execute(command)
                .also { handler -> project.getEngineDiscoveryService().attachToProcess(handler, debugPort) }
        }.onFailure { throwable ->
            console.printError("Failed to launch dmengine: ${throwable.message}")
        }.getOrNull()
    }
}
