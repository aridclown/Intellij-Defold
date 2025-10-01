package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldProjectRunner
import com.aridclown.intellij.defold.DefoldRunRequest
import com.aridclown.intellij.defold.DefoldProjectService.Companion.createConsole
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project

/**
 * Shared helpers for Defold program runners that trigger a Defold build before running.
 */
abstract class BaseDefoldProgramRunner : GenericProgramRunner<RunnerSettings>() {

    protected fun createConsole(project: Project): ConsoleView = project.createConsole()

    /**
     * Starts a Defold build/run cycle using the provided request.
     */
    protected fun launch(request: DefoldRunRequest?): Boolean {
        val runRequest = request ?: return false
        DefoldProjectRunner.run(runRequest)
        return true
    }
}
