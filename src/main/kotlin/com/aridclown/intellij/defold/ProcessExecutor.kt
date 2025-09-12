package com.aridclown.intellij.defold

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

    fun executeInBackground(
        project: Project,
        title: String,
        command: GeneralCommandLine,
        onSuccess: () -> Unit = {},
        onFailure: (Int) -> Unit = {}
    ) {
        getApplication().executeOnPooledThread {
            runBlocking {
                withBackgroundProgress(project, title, true) {
                    runCatching {
                        OSProcessHandler(command).apply {
                            console.attachToProcess(this)
                            addProcessListener(ProcessTerminationListener(onSuccess, onFailure))
                            startNotify()
                            waitFor()
                        }
                    }.onFailure { console.printError("Process execution failed: ${it.message}") }
                }
            }
        }
    }

    fun execute(command: GeneralCommandLine): OSProcessHandler = OSProcessHandler(command).apply {
        console.attachToProcess(this)
        startNotify()
    }

    fun executeAndWait(command: GeneralCommandLine): Int {
        val handler = OSProcessHandler(command).apply {
            console.attachToProcess(this)
            startNotify()
            waitFor()
        }
        return handler.exitCode ?: -1
    }
}

private fun ConsoleView.printError(message: String) {
    print("$message\n", ERROR_OUTPUT)
}

private fun ConsoleView.printSuccess(message: String) {
    print("$message\n", NORMAL_OUTPUT)
}
