package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldCoroutineService.Companion.launch
import com.aridclown.intellij.defold.util.NotificationService.notifyError
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import java.io.IOException
import kotlin.io.path.Path
import kotlin.io.path.pathString

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
            Platform.WINDOWS -> {
                val defoldProgram = Path(DefoldDefaults.getDefoldInstallPath(), DefoldDefaults.getDefoldProcess()).pathString
                val gameProjectFile = Path(projectPath, DefoldConstants.GAME_PROJECT_FILE).pathString
                GeneralCommandLine(defoldProgram, gameProjectFile)
            }
            Platform.MACOS -> createMacLaunchCommand(projectPath)
            Platform.LINUX -> GeneralCommandLine("xdg-open", projectPath)
            Platform.UNKNOWN -> error("Unknown platform: $platform")
        }

    private fun createMacLaunchCommand(projectPath: String): GeneralCommandLine =
        if (isDefoldProcessRunning()) {
            GeneralCommandLine("osascript", "-e", "activate application \"Defold\"")
        } else {
            val gameProjectFile = Path(projectPath, DefoldConstants.GAME_PROJECT_FILE).pathString
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