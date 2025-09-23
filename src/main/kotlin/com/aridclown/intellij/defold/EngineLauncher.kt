package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.DEFAULT_MOBDEBUG_PORT
import com.aridclown.intellij.defold.DefoldConstants.INI_DEBUG_INIT_SCRIPT_VALUE
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.openapi.project.Project
import java.io.File
import kotlin.io.path.Path

/**
 * Handles launching the Defold engine after a successful build
 */
class EngineRunner(
    private val console: ConsoleView,
    private val processExecutor: ProcessExecutor
) {

    fun launchEngine(
        project: Project,
        enginePath: File,
        enableDebugScript: Boolean,
        debugPort: Int?
    ): OSProcessHandler? = runCatching {
        val workspace = project.basePath
            ?: throw IllegalStateException("Project has no base path")

        val command = GeneralCommandLine(enginePath.absolutePath)
            .withWorkingDirectory(Path(workspace))

        if (enableDebugScript) {
            command.withParameters("--config=bootstrap.debug_init_script=$INI_DEBUG_INIT_SCRIPT_VALUE")
            val port = debugPort ?: DEFAULT_MOBDEBUG_PORT
            command.withEnvironment("MOBDEBUG_PORT", port.toString())
        }

        processExecutor.execute(command)
    }.onFailure { throwable ->
        console.print("Failed to launch dmengine: ${throwable.message}\n", ERROR_OUTPUT)
    }.getOrNull()
}
