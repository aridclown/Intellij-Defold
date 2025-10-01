package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.BOB_MAIN_CLASS
import com.aridclown.intellij.defold.DefoldProjectService.Companion.rootProjectFolder
import com.aridclown.intellij.defold.process.BackgroundProcessRequest
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configuration.EnvironmentVariablesData.DEFAULT
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

    fun buildProject(request: BuildRequest): Result<Unit> = runCatching {
        val projectFolder = request.project.rootProjectFolder
            ?: throw IllegalStateException("This is not a valid Defold project")

        val command = createBuildCommand(request.config, projectFolder.path, request.commands)
            .applyEnvironment(request.envData)

        processExecutor.executeInBackground(
            BackgroundProcessRequest(
                project = request.project,
                title = "Building Defold project",
                command = command,
                onSuccess = {
                    console.print("Build successful\n", NORMAL_OUTPUT)
                    request.onSuccess()
                },
                onFailure = { exitCode ->
                    console.print("Bob build failed (exit code $exitCode)\n", ERROR_OUTPUT)
                    request.onFailure(exitCode)
                }
            )
        )
    }

    private fun createBuildCommand(
        config: DefoldEditorConfig,
        projectPath: String,
        commands: List<String>
    ): GeneralCommandLine {
        val parameters = listOf(
            "-cp",
            config.editorJar,
            BOB_MAIN_CLASS,
            "--variant=debug"
        ) + commands

        return GeneralCommandLine(config.javaBin)
            .withParameters(parameters)
            .withWorkingDirectory(Path(projectPath))
    }
}

private val DEFAULT_BUILD_COMMANDS = listOf("build")

data class BuildRequest(
    val project: Project,
    val config: DefoldEditorConfig,
    val envData: EnvironmentVariablesData = DEFAULT,
    val commands: List<String> = DEFAULT_BUILD_COMMANDS,
    val onSuccess: () -> Unit,
    val onFailure: (Int) -> Unit = {}
)
