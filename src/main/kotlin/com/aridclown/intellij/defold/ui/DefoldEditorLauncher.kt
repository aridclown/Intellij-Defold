package com.aridclown.intellij.defold.ui

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.aridclown.intellij.defold.Platform
import com.aridclown.intellij.defold.Platform.*
import com.aridclown.intellij.defold.process.DefoldCoroutineService.Companion.launch
import com.aridclown.intellij.defold.ui.NotificationService.notifyError
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import java.io.IOException
import kotlin.io.path.Path

/**
 * Launches the Defold editor using a background task to keep the UI responsive.
 */
class DefoldEditorLauncher(private val project: Project) {

    fun openDefoldEditor(workspaceProjectPath: String) {
        project.launch {
            runCatching {
                val command = createLaunchCommand(workspaceProjectPath)
                executeAndWait(command)
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
    }

    private fun createLaunchCommand(projectPath: String): GeneralCommandLine =
        when (val platform = Platform.current()) {
            WINDOWS -> GeneralCommandLine("cmd", "/c", "start", "Defold", "\"$projectPath\"")
            MACOS -> createMacLaunchCommand(projectPath)
            LINUX -> GeneralCommandLine("xdg-open", projectPath)
            UNKNOWN -> error("Unknown platform: $platform")
        }

    private fun createMacLaunchCommand(projectPath: String): GeneralCommandLine =
        if (isDefoldProcessRunning()) {
            GeneralCommandLine("osascript", "-e", "activate application \"Defold\"")
        } else {
            val gameProjectFile = Path(projectPath, GAME_PROJECT_FILE).toString()
            GeneralCommandLine("open", "-a", "Defold", gameProjectFile)
        }

    private fun executeAndWait(command: GeneralCommandLine) {
        try {
            val process = command.createProcess()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                error("Command exited with code $exitCode")
            }
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw exception
        } catch (exception: IOException) {
            throw exception
        }
    }

    private fun isDefoldProcessRunning(): Boolean = runCatching {
        val process = GeneralCommandLine("pgrep", "-x", "Defold").createProcess()
        process.waitFor() == 0
    }.getOrDefault(false)
}
