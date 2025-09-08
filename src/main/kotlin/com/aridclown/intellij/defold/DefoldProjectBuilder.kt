package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldProjectService.Companion.getService
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import com.intellij.openapi.project.Project
import java.nio.file.Paths

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
        onBuildSuccess: () -> Unit
    ): Result<Unit> = runCatching {
        val projectFolder = project.getService().getDefoldProjectFolder()
            ?: throw IllegalStateException("This is not a valid Defold project")

        val command = createBuildCommand(config, projectFolder.path)

        processExecutor.executeInBackground(
            project = project,
            title = "Building Defold project",
            command = command,
            onSuccess = {
                console.print("Build successful\n", NORMAL_OUTPUT)
                onBuildSuccess()
            },
            onFailure = { exitCode ->
                console.print("Bob build failed (exit code $exitCode)\n", ERROR_OUTPUT)
            }
        )
    }

    private fun createBuildCommand(config: DefoldEditorConfig, projectPath: String): GeneralCommandLine {
        val parameters = listOf(
            "-cp",
            config.editorJar,
            "com.dynamo.bob.Bob",
            "--variant=debug",
            "build"
        )

        return GeneralCommandLine(config.javaBin)
            .withParameters(parameters)
            .withWorkingDirectory(Paths.get(projectPath))
    }
}
