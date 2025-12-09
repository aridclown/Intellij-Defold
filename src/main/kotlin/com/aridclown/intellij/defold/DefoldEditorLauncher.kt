package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldCoroutineService.Companion.launch
import com.aridclown.intellij.defold.util.NotificationService.notifyError
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Job
import java.io.IOException

/**
 * Launches the Defold editor using a background task to keep the UI responsive.
 */
class DefoldEditorLauncher(
    private val project: Project,
    private val commandBuilder: DefoldCommandBuilder = DefoldCommandBuilder()
) {
    fun openDefoldEditor(workspaceProjectPath: String): Job = project.launch {
        runCatching {
            commandBuilder.createLaunchCommand(workspaceProjectPath).also(::startProcess)
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

    private fun startProcess(command: GeneralCommandLine) {
        try {
            command.createProcess()
        } catch (exception: IOException) {
            throw exception
        }
    }
}
