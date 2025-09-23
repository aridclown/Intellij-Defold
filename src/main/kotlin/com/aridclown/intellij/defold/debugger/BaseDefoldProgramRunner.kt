package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldEditorConfig
import com.aridclown.intellij.defold.DefoldProjectRunner
import com.aridclown.intellij.defold.ui.DefoldLogHyperlinkFilter
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.openapi.project.Project

/**
 * Shared helpers for Defold program runners that trigger a Defold build before running.
 */
abstract class BaseDefoldProgramRunner : GenericProgramRunner<RunnerSettings>() {

    protected fun createConsole(project: Project): ConsoleView {
        val console = TextConsoleBuilderFactory.getInstance()
            .createBuilder(project)
            .console
        console.addMessageFilter(DefoldLogHyperlinkFilter(project))
        return console
    }

    /**
     * Loads the Defold editor configuration and starts a build when available,
     * reporting configuration issues to the provided console.
     */
    protected fun launchBuild(
        project: Project,
        console: ConsoleView,
        enableDebugScript: Boolean,
        onStarted: (OSProcessHandler) -> Unit
    ): Boolean {
        val config = DefoldEditorConfig.loadEditorConfig()
        if (config == null) {
            console.print("Invalid Defold editor path.\n", ERROR_OUTPUT)
            return false
        }

        DefoldProjectRunner.runBuild(
            project = project,
            config = config,
            console = console,
            enableDebugScript = enableDebugScript,
            onEngineStarted = onStarted
        )
        return true
    }
}
