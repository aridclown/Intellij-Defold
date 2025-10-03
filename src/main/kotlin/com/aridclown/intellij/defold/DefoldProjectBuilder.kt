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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.io.path.Path

/**
 * Handles building Defold projects using Bob
 */
class DefoldProjectBuilder(
    private val console: ConsoleView,
    private val processExecutor: ProcessExecutor
) {

    suspend fun buildProject(request: BuildRequest): Result<Unit> {
        val projectFolder = request.project.rootProjectFolder
            ?: return Result.failure(IllegalStateException("This is not a valid Defold project"))

        val command = createBuildCommand(request.config, projectFolder.path, request.commands)
            .applyEnvironment(request.envData)

        return suspendCancellableCoroutine { continuation ->
            val job = try {
                processExecutor.executeInBackground(
                    BackgroundProcessRequest(
                        project = request.project,
                        title = "Building Defold project",
                        command = command,
                        onSuccess = {
                            console.print("Build successful\n", NORMAL_OUTPUT)
                            val result = runCatching { request.onSuccess() }.fold(
                                onSuccess = { Result.success(Unit) },
                                onFailure = { Result.failure(it) }
                            )
                            if (continuation.isActive) continuation.resume(result)
                        },
                        onFailure = { exitCode ->
                            console.print("Bob build failed (exit code $exitCode)\n", ERROR_OUTPUT)
                            val failure = runCatching { request.onFailure(exitCode) }.fold(
                                onSuccess = { Result.failure(BuildProcessFailedException(exitCode)) },
                                onFailure = { Result.failure<Unit>(it) }
                            )
                            if (continuation.isActive) continuation.resume(failure)
                        }
                    )
                )
            } catch (throwable: Throwable) {
                if (continuation.isActive) {
                    continuation.resume(Result.failure(throwable))
                }
                return@suspendCancellableCoroutine
            }

            job.invokeOnCompletion { throwable ->
                if (throwable != null && continuation.isActive) {
                    continuation.resume(Result.failure(throwable))
                }
            }

            continuation.invokeOnCancellation { cause ->
                if (cause is CancellationException) job.cancel(cause)
            }
        }
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
    val onSuccess: () -> Unit = {},
    val onFailure: (Int) -> Unit = {}
)

internal class BuildProcessFailedException(val exitCode: Int) : RuntimeException(
    "Bob build failed (exit code $exitCode)"
)
