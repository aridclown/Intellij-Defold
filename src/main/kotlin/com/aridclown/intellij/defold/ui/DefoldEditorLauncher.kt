package com.aridclown.intellij.defold.ui

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.aridclown.intellij.defold.Platform
import com.aridclown.intellij.defold.Platform.*
import com.aridclown.intellij.defold.ProcessExecutor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Paths

/**
 * Handles opening the Defold editor application across different platforms
 */
class DefoldEditorLauncher(
    private val console: ConsoleView,
    private val processExecutor: ProcessExecutor
) {

    fun openDefoldEditor(workspaceProjectPath: String) = runCatching {
        val platform = Platform.current()

        console.print("Opening Defold Editor for platform: $platform\n", NORMAL_OUTPUT)

        // Execute asynchronously to avoid EDT blocking
        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                val command = when (platform) {
                    WINDOWS -> createWindowsCommand(workspaceProjectPath)
                    MACOS -> createMacOSCommandAsync(workspaceProjectPath)
                    LINUX -> createLinuxCommand(workspaceProjectPath)
                    UNKNOWN -> throw UnsupportedOperationException("Unknown platform: $platform")
                }

                try {
                    processExecutor.execute(command)
                    ApplicationManager.getApplication().invokeLater {
                        console.print("Defold Editor launch command executed\n", NORMAL_OUTPUT)
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        console.print("Failed to execute Defold Editor command: ${e.message}\n", ERROR_OUTPUT)
                    }
                }
            }
        }
    }.onFailure { throwable ->
        console.print("Failed to open Defold Editor: ${throwable.message}\n", ERROR_OUTPUT)
    }

    private fun createWindowsCommand(projectPath: String): GeneralCommandLine =
        GeneralCommandLine("cmd", "/c", "start", "Defold", "\"$projectPath\"")

    private suspend fun createMacOSCommandAsync(projectPath: String): GeneralCommandLine =
        withContext(Dispatchers.IO) {
            // Check if Defold is already running
            if (isDefoldProcessRunningAsync()) {
                // Activate an existing Defold instance
                GeneralCommandLine("osascript", "-e", "activate application \"Defold\"")
            } else {
                // Open Defold with the project
                val gameProjectFile = Paths.get(projectPath, GAME_PROJECT_FILE).toString()
                GeneralCommandLine("open", "-a", "Defold", gameProjectFile)
            }
        }

    private fun createLinuxCommand(projectPath: String): GeneralCommandLine =
        GeneralCommandLine("xdg-open", projectPath)

    private suspend fun isDefoldProcessRunningAsync(): Boolean = withContext(Dispatchers.IO) {
        try {
            val command = GeneralCommandLine("pgrep", "-x", "Defold")
            processExecutor.executeAndWait(command) == 0
        } catch (e: Exception) {
            false
        }
    }
}
