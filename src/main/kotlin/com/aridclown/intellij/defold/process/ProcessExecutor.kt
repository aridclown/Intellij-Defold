package com.aridclown.intellij.defold.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.runBlocking

/**
 * Utility class for executing processes with consistent error handling and console output
 */
class ProcessExecutor(
    private val console: ConsoleView
) {

    fun executeInBackground(request: BackgroundProcessRequest) {
        getApplication().executeOnPooledThread {
            runBlocking {
                withBackgroundProgress(request.project, request.title, true) {
                    runCatching {
                        DefoldProcessHandler(request.command).apply {
                            console.attachToProcess(this)
                            addProcessListener(ProcessTerminationListener(request.onSuccess, request.onFailure))
                            startNotify()
                            waitFor()
                        }
                    }.onFailure { console.printError("Process execution failed: ${it.message}") }
                }
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

private fun ConsoleView.printError(message: String) {
    print("$message\n", ERROR_OUTPUT)
}

private fun ConsoleView.printSuccess(message: String) {
    print("$message\n", NORMAL_OUTPUT)
}
