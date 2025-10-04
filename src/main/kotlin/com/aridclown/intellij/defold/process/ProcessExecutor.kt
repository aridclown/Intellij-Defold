package com.aridclown.intellij.defold.process

import com.aridclown.intellij.defold.printError
import com.aridclown.intellij.defold.process.DefoldCoroutineService.Companion.launch
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.Job

/**
 * Utility class for executing processes with consistent error handling and console output
 */
class ProcessExecutor(
    private val console: ConsoleView
) {

    fun executeInBackground(request: BackgroundProcessRequest): Job = with(request) {
        project.launch {
            withBackgroundProgress(project, title, false) {
                runCatching {
                    DefoldProcessHandler(command).apply {
                        addProcessListener(ProcessTerminationListener(onSuccess, onFailure))
                        startNotify()
                        waitFor()
                    }
                }.onFailure { console.printError("Process execution failed: ${it.message}") }
            }
        }
    }

    fun execute(command: GeneralCommandLine): OSProcessHandler = DefoldProcessHandler(command).apply {
        console.attachToProcess(this)
        startNotify()
    }

    fun executeAndWait(command: GeneralCommandLine): Int {
        val handler = DefoldProcessHandler(command).apply {
            console.attachToProcess(this)
            startNotify()
            waitFor()
        }
        return handler.exitCode ?: -1
    }
}

data class BackgroundProcessRequest(
    val project: Project,
    val title: String,
    val command: GeneralCommandLine,
    val onSuccess: () -> Unit = {},
    val onFailure: (Int) -> Unit = {}
)
