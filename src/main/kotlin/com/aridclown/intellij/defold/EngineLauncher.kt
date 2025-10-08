package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.DEFAULT_MOBDEBUG_PORT
import com.aridclown.intellij.defold.DefoldConstants.INI_DEBUG_INIT_SCRIPT_VALUE
import com.aridclown.intellij.defold.engine.DefoldEngineDiscoveryService.Companion.getEngineDiscoveryService
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

/**
 * Handles launching the Defold engine after a successful build
 */
class EngineRunner(
    private val console: ConsoleView,
    private val processExecutor: ProcessExecutor
) {

    fun launchEngine(
        project: Project,
        enginePath: Path,
        enableDebugScript: Boolean,
        debugPort: Int?,
        envData: EnvironmentVariablesData
    ): OSProcessHandler? = runCatching {
        val workspace = project.basePath
            ?: throw IllegalStateException("Project has no base path")

        val command = GeneralCommandLine(enginePath.toAbsolutePath().pathString)
            .withWorkingDirectory(Path(workspace))
            .applyEnvironment(envData)

        if (enableDebugScript) {
            val port = debugPort ?: DEFAULT_MOBDEBUG_PORT
            command
                .withParameters("--config=bootstrap.debug_init_script=$INI_DEBUG_INIT_SCRIPT_VALUE")
                .withEnvironment("MOBDEBUG_PORT", port.toString())
        }

        processExecutor.execute(command)
            .also(project.getEngineDiscoveryService()::attachToProcess)
    }.onFailure { throwable ->
        console.printError("Failed to launch dmengine: ${throwable.message}")
    }.getOrNull()
}
