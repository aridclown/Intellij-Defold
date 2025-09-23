package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.BOB_MAIN_CLASS
import com.aridclown.intellij.defold.DefoldProjectService.Companion.getService
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import com.intellij.openapi.project.Project
import kotlin.io.path.Path

/**
 * Handles building Defold projects using Bob
 */
class DefoldProjectBuilder(
    private val console: ConsoleView,
    private val processExecutor: ProcessExecutor
) {

    fun buildProject(
        project: Project,
        config: DefoldEditorConfig,
        onBuildSuccess: () -> Unit,
        onBuildFailure: (Int) -> Unit = {}
    ): Result<Unit> = runCatching {
        val projectFolder = project.getService().rootProjectFolder
            ?: throw IllegalStateException("This is not a valid Defold project")

        processExecutor.executeInBackground(
            project = project,
            title = "Building Defold project",
            command = createBuildCommand(config, projectFolder.path),
            onSuccess = {
                console.print("Build successful\n", NORMAL_OUTPUT)
                onBuildSuccess()
            },
            onFailure = { exitCode ->
                console.print("Bob build failed (exit code $exitCode)\n", ERROR_OUTPUT)
                onBuildFailure(exitCode)
            }
        )
    }

    private fun createBuildCommand(config: DefoldEditorConfig, projectPath: String): GeneralCommandLine {
        val parameters = listOf(
            "-cp",
            config.editorJar,
            BOB_MAIN_CLASS,
            "--variant=debug",
            "build"
        )

        return GeneralCommandLine(config.javaBin)
            .withParameters(parameters)
            .withWorkingDirectory(Path(projectPath))
    }
}
