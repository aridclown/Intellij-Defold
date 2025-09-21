package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldEditorConfig
import com.aridclown.intellij.defold.DefoldProjectRunner
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.openapi.project.Project

typealias BuildLauncher = (
    project: Project,
    config: DefoldEditorConfig,
    console: ConsoleView,
    onComplete: (OSProcessHandler) -> Unit
) -> Unit

/**
 * Shared helpers for Defold program runners that trigger a Defold build before running.
 */
abstract class BaseDefoldProgramRunner : GenericProgramRunner<RunnerSettings>() {

    protected fun createConsole(project: Project): ConsoleView = TextConsoleBuilderFactory.getInstance()
        .createBuilder(project)
        .console

    /**
     * Loads the Defold editor configuration and starts a build when available,
     * reporting configuration issues to the provided console.
     */
    protected fun launchBuild(
        project: Project,
        console: ConsoleView
    ): OSProcessHandler? {
        val config = DefoldEditorConfig.loadEditorConfig()
        if (config == null) {
            console.print("Invalid Defold editor path.\n", ERROR_OUTPUT)
            return null
        }

        var gameProcess: OSProcessHandler? = null
        DefoldProjectRunner.runBuild(
            project = project,
            config = config,
            console = console,
            onEngineStarted = { handler -> gameProcess = handler }
        )

        return gameProcess
    }
}
