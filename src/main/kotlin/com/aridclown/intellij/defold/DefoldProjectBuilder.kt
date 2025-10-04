package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.BOB_MAIN_CLASS
import com.aridclown.intellij.defold.DefoldProjectService.Companion.rootProjectFolder
import com.aridclown.intellij.defold.process.BackgroundProcessRequest
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configuration.EnvironmentVariablesData.DEFAULT
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.ui.ConsoleView
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

        val buildResult = awaitBuildCompletion(request, command)

        return buildResult.fold(
            onSuccess = {
                console.printInfo("Build successful")
                runCatching { request.onSuccess() }
            },
            onFailure = { throwable ->
                if (throwable is BuildProcessFailedException) {
                    console.printError("Bob build failed (exit code ${throwable.exitCode})")
                    runCatching { request.onFailure(throwable.exitCode) }
                        .exceptionOrNull()?.let { return@fold Result.failure<Unit>(it) }
                }

                Result.failure(throwable)
            }
        )
    }

    private suspend fun awaitBuildCompletion(
        request: BuildRequest,
        command: GeneralCommandLine
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        val job = runCatching {
            processExecutor.executeInBackground(
                BackgroundProcessRequest(
                    project = request.project,
                    title = "Building Defold project",
                    command = command,
                    onSuccess = {
                        if (continuation.isActive) continuation.resume(Result.success(Unit))
                    },
                    onFailure = { exitCode ->
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(BuildProcessFailedException(exitCode)))
                        }
                    }
                )
            )
        }.getOrElse { throwable ->
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
