package com.aridclown.intellij.defold.process

import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.ProcessEvent

/**
 * Process listener that handles termination events with success/failure callbacks
 */
class ProcessTerminationListener(
    private val onSuccess: () -> Unit,
    private val onFailure: (Int) -> Unit
) : CapturingProcessAdapter() {

    override fun processTerminated(event: ProcessEvent) {
        val exitCode = event.exitCode
        if (exitCode == 0) {
            onSuccess()
        } else {
            onFailure(exitCode)
        }
    }
}
