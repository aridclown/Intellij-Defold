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
        executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is DefoldMobDebugRunConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor =
        with(environment) {
            val config = runProfile as DefoldMobDebugRunConfiguration
            val mappings = when {
                config.localRoot.isNotBlank() && config.remoteRoot.isNotBlank() -> mapOf(config.localRoot to config.remoteRoot)
                else -> emptyMap()
            }

            val console = createConsole(project)

            var gameProcess: OSProcessHandler? = null
            launch(
                project = project,
                console = console,
                enableDebugScript = true,
                debugPort = config.port,
                onStarted = { handler -> gameProcess = handler }
            )

            XDebuggerManager.getInstance(project).startSession(environment, object : XDebugProcessStarter() {
                override fun start(session: XDebugSession) = MobDebugProcess(
                    session = session,
                    pathMapper = MobDebugPathMapper(mappings),
                    project = project,
                    host = config.host,
                    port = config.port,
                    console = console,
                    gameProcess = gameProcess,
                    localBaseDir = config.localRoot.ifBlank { project.basePath },
                    remoteBaseDir = config.remoteRoot.ifBlank { null }
                )
            }).runContentDescriptor
        }
}
