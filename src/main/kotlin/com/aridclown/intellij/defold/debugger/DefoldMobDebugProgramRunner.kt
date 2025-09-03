package com.aridclown.intellij.defold.debugger

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner

class DefoldMobDebugProgramRunner : GenericProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = "DefoldMobDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is DefoldMobDebugRunConfiguration
    }

    override fun execute(environment: ExecutionEnvironment, state: RunProfileState) {
        state.execute(environment.executor, this)
    }
}
