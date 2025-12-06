package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.BOB_MAIN_CLASS
import com.aridclown.intellij.defold.DefoldProjectService.Companion.rootProjectFolder
import com.aridclown.intellij.defold.process.BackgroundProcessRequest
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.aridclown.intellij.defold.util.printError
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configuration.EnvironmentVariablesData.DEFAULT
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.io.path.Path

/**
 * Handles building Defold projects using Bob
 */
class ProjectBuilder(
    private val processExecutor: ProcessExecutor
) {
    suspend fun buildProject(
        request: BuildRequest,
        buildMessage: String = DEFAULT_BUILD_MESSAGE
    ): Result<Unit> {
        val projectFolder = request.project.rootProjectFolder
            ?: return Result.failure(IllegalStateException("This is not a valid Defold project"))

        val command = createBuildCommand(request.config, projectFolder.path, request.commands)
            .applyEnvironment(request.envData)

        val buildResult = awaitBuildCompletion(request, command, buildMessage)

        return buildResult.fold(
            onSuccess = {
                runCatching { request.onSuccess() }
            },
            onFailure = { throwable ->
                if (throwable is BuildProcessFailedException) {
                    processExecutor.console?.printError(throwable.message.orEmpty())
                    runCatching { request.onFailure(throwable.exitCode) }
                        .exceptionOrNull()
                        ?.let { return@fold Result.failure(it) }
                }

                Result.failure(throwable)
            }
        )
    }

    private suspend fun awaitBuildCompletion(
        request: BuildRequest,
        command: GeneralCommandLine,
        buildMessage: String
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        val job = runCatching {
            processExecutor.executeInBackground(
                BackgroundProcessRequest(
                    project = request.project,
                    title = buildMessage,
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

data class BuildRequest(
    val project: Project,
    val config: DefoldEditorConfig,
    val envData: EnvironmentVariablesData = DEFAULT,
    val commands: List<String> = DEFAULT_BUILD_COMMANDS,
    val onSuccess: () -> Unit = {},
    val onFailure: (Int) -> Unit = {}
)

internal class BuildProcessFailedException(
    val exitCode: Int
) : RuntimeException("Bob build failed (exit code $exitCode)")

private val DEFAULT_BUILD_COMMANDS = listOf("build")
private const val DEFAULT_BUILD_MESSAGE = "Building Defold project"
