package com.aridclown.intellij.defold.debugger

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.runners.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor

class DefoldMobDebugProgramRunner : GenericProgramRunner<RunnerSettings?>() {
    override fun getRunnerId(): String = "DefoldMobDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is DefoldMobDebugRunConfiguration
    }

    override fun execute(environment: ExecutionEnvironment) {
        val state = environment.state ?: return
        state.execute(environment.executor, this)
    }
}
