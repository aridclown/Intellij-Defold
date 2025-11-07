package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldCoroutineService.Companion.launch
import com.aridclown.intellij.defold.util.NotificationService.notifyError
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Job
import java.io.IOException
import java.lang.Thread.currentThread

/**
 * Launches the Defold editor using a background task to keep the UI responsive.
 */
class DefoldEditorLauncher(
    private val project: Project,
    private val commandBuilder: DefoldCommandBuilder = DefoldCommandBuilder()
) {

    fun openDefoldEditor(workspaceProjectPath: String): Job = project.launch {
        runCatching {
            commandBuilder.createLaunchCommand(workspaceProjectPath).also(::executeAndWait)
        }.onFailure { error ->
            if (error !is ProcessCanceledException) {
                project.notifyError(
                    title = "Defold",
                    content = "Failed to open Defold editor: ${error.message ?: "unknown error"}"
                )
            }
            throw error
        }
    }

    private fun executeAndWait(command: GeneralCommandLine) {
        try {
            val process = command.createProcess()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                error("Command exited with code $exitCode")
            }
        } catch (exception: InterruptedException) {
            currentThread().interrupt()
            throw exception
        } catch (exception: IOException) {
            throw exception
        }
    }
}
