package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldEditorConfig
import com.aridclown.intellij.defold.DefoldProjectRunner
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager

class DefoldMobDebugProgramRunner : GenericProgramRunner<RunnerSettings>() {

    companion object {
        const val DEFOLD_RUNNER_ID = "DefoldMobDebugRunner"
    }

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

            var gameProcess: OSProcessHandler? = null
            val defoldCfg = DefoldEditorConfig.loadEditorConfig()
            if (defoldCfg == null) {
                console.print("Invalid Defold editor path.\n", ERROR_OUTPUT)
            } else {
                DefoldProjectRunner.runBuild(project, defoldCfg, console)
            }

            XDebuggerManager.getInstance(project).startSession(environment, object : XDebugProcessStarter() {
                override fun start(session: XDebugSession) = MobDebugProcess(
                    session = session,
                    pathMapper = mapper,
                    project = project,
                    host = config.host,
                    port = config.port,
                    console = console,
                    gameProcess = gameProcess
                )
            }).runContentDescriptor
        }
}