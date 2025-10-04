package com.aridclown.intellij.defold

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project

data class DefoldRunRequest(
    val project: Project,
    val config: DefoldEditorConfig,
    val console: ConsoleView,
    val enableDebugScript: Boolean = false,
    val debugPort: Int? = null,
    val envData: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
    val buildCommands: List<String> = listOf("build"),
    val onEngineStarted: (OSProcessHandler) -> Unit = {}
) {
    companion object {
        fun loadFromEnvironment(
            project: Project,
            console: ConsoleView,
            enableDebugScript: Boolean,
            debugPort: Int? = null,
            envData: EnvironmentVariablesData,
            buildCommands: List<String> = listOf("build"),
            onEngineStarted: (OSProcessHandler) -> Unit = {}
        ): DefoldRunRequest? {
            val config = DefoldEditorConfig.loadEditorConfig()
            if (config == null) {
                console.printError("Invalid Defold editor path.")
                return null
            }

            return DefoldRunRequest(
                project, config, console, enableDebugScript, debugPort, envData, buildCommands, onEngineStarted
            )
        }
    }
}
