package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.process.DeferredProcessHandler
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager.getApplication

open class DefoldProjectRunProgramRunner : BaseDefoldProgramRunner() {

    companion object {
        const val DEFOLD_RUNNER_ID = "DefoldProjectRunRunner"
    }

    override fun getRunnerId(): String = DEFOLD_RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultRunExecutor.EXECUTOR_ID && profile is DefoldMobDebugRunConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? =
        with(environment) {
            val console = createConsole(project)
            val processHandler = DeferredProcessHandler()
                .also { console.attachToProcess(it) }

            launchBuild(
                project = project,
                console = console,
                enableDebugScript = false,
                onStarted = { handler ->
                    getApplication().invokeLater { processHandler.attach(handler) }
                }
            )

            val executionResult = DefaultExecutionResult(console, processHandler)
            RunContentBuilder(executionResult, environment)
                .showRunContent(environment.contentToReuse)
        }
}
