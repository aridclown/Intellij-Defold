package com.aridclown.intellij.defold.debugger

import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueNode

/**
 * Basic XValue implementation showing the string representation of a variable.
 */
class MobDebugValue(private val variable: MobDebugVariable) : XValue() {
    override fun computePresentation(node: XValueNode, place: com.intellij.xdebugger.XValuePlace) {
        node.setPresentation(null, variable.value, false)
    }
}

