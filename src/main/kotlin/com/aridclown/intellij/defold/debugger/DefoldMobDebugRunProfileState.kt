package com.aridclown.intellij.defold.debugger

import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ProgramRunner

/**
 * Simple data holder for DefoldMobDebugRunConfiguration state.
 * The actual execution logic is handled by DefoldMobDebugProgramRunner.
 */
class DefoldMobDebugRunProfileState : RunProfileState {

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        // This method should not be called when using a ProgramRunner
        // The ProgramRunner's doExecute() method handles execution instead
        throw UnsupportedOperationException("Use DefoldMobDebugProgramRunner for execution")
    }
}
