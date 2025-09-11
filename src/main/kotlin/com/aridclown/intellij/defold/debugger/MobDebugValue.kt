package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.value.MobRValue.Num
import com.aridclown.intellij.defold.debugger.value.MobRValue.Str
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XNumericValuePresentation
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation
import com.intellij.xdebugger.frame.presentation.XStringValuePresentation

/**
 * Basic XValue implementation showing the string representation of a variable.
 */
class MobDebugValue(private val variable: MobVariable) : XValue() {
    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        val v = variable.value
        val xValuePresentation = when (v) {
            is Str -> object : XStringValuePresentation(v.value) {
                override fun getType() = v.typeLabel
            }

            is Num -> object : XNumericValuePresentation(v.value) {
                override fun getType() = v.typeLabel
            }

            else -> XRegularValuePresentation(v.preview, v.typeLabel)
        }

        node.setPresentation(v.icon, xValuePresentation, v.hasChildren)
    }
}