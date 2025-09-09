package com.aridclown.intellij.defold.debugger

import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace

/**
 * Basic XValue implementation showing the string representation of a variable.
 */
class MobDebugValue(private val variable: MobDebugVariable) : XValue() {
    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        node.setPresentation(null, variable.name, variable.value, false)
    }
}

