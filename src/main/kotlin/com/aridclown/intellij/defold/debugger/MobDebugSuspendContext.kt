package com.aridclown.intellij.defold.debugger

import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext

/**
 * Suspend context containing one or more execution stacks (per coroutine).
 */
class MobDebugSuspendContext(private val executionStacks: List<XExecutionStack>) : XSuspendContext() {

    override fun getActiveExecutionStack(): XExecutionStack? = executionStacks.firstOrNull()

    override fun getExecutionStacks(): Array<XExecutionStack> = executionStacks.toTypedArray()
}
