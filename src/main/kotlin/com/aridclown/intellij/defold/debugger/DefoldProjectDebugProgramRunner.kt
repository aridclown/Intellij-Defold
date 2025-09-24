package com.aridclown.intellij.defold.debugger

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager

open class DefoldProjectDebugProgramRunner : BaseDefoldProgramRunner() {

    companion object {
        const val DEFOLD_RUNNER_ID = "DefoldMobDebugRunner"
    }

    override fun getRunnerId(): String = DEFOLD_RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is MobDebugRunConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor =
        with(environment) {
            val config = runProfile as MobDebugRunConfiguration
            val console = createConsole(project)

            var gameProcess: OSProcessHandler? = null
            launch(
                project = project,
                console = console,
                enableDebugScript = true,
                debugPort = config.port,
                envData = config.envData,
                onStarted = { handler -> gameProcess = handler }
            )

            XDebuggerManager.getInstance(project).startSession(environment, object : XDebugProcessStarter() {
                override fun start(session: XDebugSession) = MobDebugProcess(
                    session = session,
                    pathMapper = MobDebugPathMapper(config.getMappingSettings()),
                    project = project,
                    configData = config,
                    console = console,
                    gameProcess = gameProcess
                )
            }).runContentDescriptor
        }
}
