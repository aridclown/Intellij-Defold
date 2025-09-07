package com.aridclown.intellij.defold.debugger

import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext

/**
 * Suspend context containing a fixed stack of frames.
 */
class MobDebugSuspendContext(private val frames: List<XStackFrame>) : XSuspendContext() {

    override fun getActiveExecutionStack(): XExecutionStack = object : XExecutionStack("Defold stack") {
        override fun getTopFrame(): XStackFrame? = frames.firstOrNull()

        override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
            container.addStackFrames(frames, true)
        }
    }
}