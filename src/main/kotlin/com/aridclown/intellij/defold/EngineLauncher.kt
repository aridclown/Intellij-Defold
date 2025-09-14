package com.aridclown.intellij.defold

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Paths

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
        host: String,
        port: Int
    ): OSProcessHandler? = runCatching {
        val workspace = project.basePath
            ?: throw IllegalStateException("Project has no base path")

        val command = GeneralCommandLine(enginePath.absolutePath)
            .withWorkingDirectory(Paths.get(workspace))
            .withEnvironment(
                "LUA_INIT",
                "mobdebug=require('mobdebug'); mobdebug.start('$host',$port)"
            )

        processExecutor.execute(command)
    }.onFailure { throwable ->
        console.print("Failed to launch dmengine: ${throwable.message}\n", ERROR_OUTPUT)
    }.getOrNull()
}