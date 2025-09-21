package com.aridclown.intellij.defold.process

import com.intellij.execution.process.*
import java.io.OutputStream

/**
 * Lightweight process handler that is returned to the platform immediately while the Defold build runs.
 * Once the engine process starts we "attach" the real [OSProcessHandler] so lifecycle events propagate
 * correctly to the Run tool window actions (start, stop, termination, etc.).
 */
internal class DeferredProcessHandler : ProcessHandler() {

    private var attachedHandler: OSProcessHandler? = null

    fun attach(handler: OSProcessHandler) {
        attachedHandler = handler
        // Forward the real process handler's state
        if (handler.isProcessTerminated) {
            notifyProcessTerminated(handler.exitCode ?: -1)
        } else {
            attachedHandler?.notifyTextAvailable(
                "Defold engine started with PID ${handler.process.pid()}\n",
                ProcessOutputTypes.STDOUT
            )

            // Listen for when the real process terminates
            handler.addProcessListener(object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    notifyProcessTerminated(event.exitCode)
                }
            })
        }
    }

    override fun destroyProcessImpl() {
        attachedHandler?.destroyProcess()
    }

    override fun detachProcessImpl() {
        attachedHandler?.detachProcess()
    }

    override fun detachIsDefault(): Boolean = false

    override fun getProcessInput(): OutputStream? = attachedHandler?.processInput
}