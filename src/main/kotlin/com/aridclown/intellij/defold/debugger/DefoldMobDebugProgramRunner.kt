package com.aridclown.intellij.defold.debugger

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager

class DefoldMobDebugProgramRunner : GenericProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = DEFOLD_RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is DefoldMobDebugRunConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor =
        with(environment) {
            val config = runProfile as DefoldMobDebugRunConfiguration
            val mappings = when {
                config.localRoot.isNotBlank() && config.remoteRoot.isNotBlank() -> mapOf(config.localRoot to config.remoteRoot)
                else -> emptyMap()
            }

            val mapper = MobDebugPathMapper(mappings)
            val console = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project)
                .console

            XDebuggerManager.getInstance(project).startSession(environment, object : XDebugProcessStarter() {
                override fun start(session: XDebugSession) = MobDebugProcess(
                    session = session,
                    pathMapper = mapper,
                    project = project,
                    host = config.host,
                    port = config.port,
                    console = console
                )
            }).runContentDescriptor
        }
}

private const val DEFOLD_RUNNER_ID: String = "defold.mob.runner"
