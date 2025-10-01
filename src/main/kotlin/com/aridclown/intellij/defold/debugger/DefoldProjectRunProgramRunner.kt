package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldRunRequest
import com.aridclown.intellij.defold.process.DeferredProcessHandler
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentDescriptor

open class DefoldProjectRunProgramRunner : BaseDefoldProgramRunner() {

    companion object {
        const val DEFOLD_RUNNER_ID = "DefoldProjectRunRunner"
    }

    override fun getRunnerId(): String = DEFOLD_RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultRunExecutor.EXECUTOR_ID && profile is MobDebugRunConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? =
        with(environment) {
            val config = runProfile as MobDebugRunConfiguration
            val console = createConsole(project)
            val processHandler = DeferredProcessHandler()
                .also(console::attachToProcess)

            val buildCommands = config.runtimeBuildCommands ?: listOf("build")
            val enableDebugScript = config.runtimeEnableDebugScript ?: false

            try {
                launch(
                    DefoldRunRequest.loadFromEnvironment(
                        project = project,
                        console = console,
                        enableDebugScript = enableDebugScript,
                        envData = config.envData,
                        buildCommands = buildCommands,
                        onEngineStarted = processHandler::attach
                    )
                )
            } finally {
                config.runtimeBuildCommands = null
                config.runtimeEnableDebugScript = null
            }

            val executionResult = DefaultExecutionResult(console, processHandler)
            RunContentBuilder(executionResult, environment)
                .showRunContent(environment.contentToReuse)
        }
}
