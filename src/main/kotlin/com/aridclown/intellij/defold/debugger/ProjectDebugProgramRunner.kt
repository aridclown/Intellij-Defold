package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldProjectService.Companion.createConsole
import com.aridclown.intellij.defold.ProjectRunner
import com.aridclown.intellij.defold.RunRequest
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager

open class ProjectDebugProgramRunner : BaseDefoldProgramRunner() {
    companion object {
        const val DEFOLD_RUNNER_ID = "DefoldMobDebugRunner"
    }

    override fun getRunnerId(): String = DEFOLD_RUNNER_ID

    override fun canRun(
        executorId: String,
        profile: RunProfile
    ): Boolean = executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is MobDebugRunConfiguration

    override fun doExecute(
        state: RunProfileState,
        environment: ExecutionEnvironment
    ): RunContentDescriptor? = with(environment) {
        val config = runProfile as MobDebugRunConfiguration
        val console = project.createConsole()

        var gameProcess: OSProcessHandler? = null
        val buildCommands = config.runtimeBuildCommands ?: listOf("build")
        val enableDebugScript = config.runtimeEnableDebugScript ?: true

        try {
            val request =
                RunRequest.loadFromEnvironment(
                    project = project,
                    console = console,
                    enableDebugScript = enableDebugScript,
                    serverPort = (50000..59999).random(),
                    debugPort = config.port,
                    envData = config.envData,
                    buildCommands = buildCommands,
                    onEngineStarted = { handler -> gameProcess = handler }
                ) ?: return null

            ProjectRunner.run(request)
        } finally {
            config.runtimeBuildCommands = null
            config.runtimeEnableDebugScript = null
        }

        XDebuggerManager
            .getInstance(project)
            .startSession(
                environment,
                object : XDebugProcessStarter() {
                    override fun start(session: XDebugSession): MobDebugProcess {
                        val pathMapper = MobDebugPathMapper(config.getMappingSettings())
                        return MobDebugProcess(
                            session,
                            pathMapper,
                            config,
                            project,
                            console,
                            gameProcess
                        )
                    }
                }
            ).runContentDescriptor
    }
}
