package com.aridclown.intellij.defold.process

import com.aridclown.intellij.defold.DefoldCoroutineService.Companion.launch
import com.aridclown.intellij.defold.util.printError
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.Job

/**
 * Utility class for executing processes with consistent error handling and console output
 */
class ProcessExecutor(
    val console: ConsoleView
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

/**
 * Process listener that handles termination events with success/failure callbacks
 */
class ProcessTerminationListener(
    private val onSuccess: () -> Unit,
    private val onFailure: (Int) -> Unit
) : CapturingProcessAdapter() {

    override fun processTerminated(event: ProcessEvent) {
        val exitCode = event.exitCode
        if (exitCode == 0) {
            onSuccess()
        } else {
            onFailure(exitCode)
        }
    }
}