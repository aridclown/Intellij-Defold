package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.lua.LuaExpr
import com.aridclown.intellij.defold.debugger.value.MobRValue
import com.aridclown.intellij.defold.debugger.value.MobRValue.Num
import com.aridclown.intellij.defold.debugger.value.MobRValue.Str
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XNumericValuePresentation
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation
import com.intellij.xdebugger.frame.presentation.XStringValuePresentation

/**
 * Basic XValue implementation showing the string representation of a variable.
 */
class MobDebugValue(
    private val variable: MobVariable,
    private val evaluator: MobDebugEvaluator,
    private val frameIndex: Int,
    private val expr: String
) : XValue() {
    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        val v = variable.value
        val presentation = when (v) {
            is Str -> object : XStringValuePresentation(v.value) {
                override fun getType() = v.typeLabel
            }

            is Num -> object : XNumericValuePresentation(v.value) {
                override fun getType() = v.typeLabel
            }

            else -> XRegularValuePresentation(v.preview, v.typeLabel)
        }

        node.setPresentation(v.icon, presentation, v.hasChildren)
    }

    override fun computeChildren(node: XCompositeNode) {
        if ((variable.value as? MobRValue.Table) == null) {
            node.addChildren(XValueChildrenList.EMPTY, true)
            return
        }

        evaluator.evaluateExpr(frameIndex, expr, onSuccess = { value ->
            if (!value.istable()) {
                node.addChildren(XValueChildrenList.EMPTY, true)
                return@evaluateExpr
            }

            val table = value.checktable()
            val list = XValueChildrenList()
            for (k in table.keys()) {
                val childName = try {
                    k.tojstring()
                } catch (_: Throwable) {
                    k.toString()
                }
                val rv = MobRValue.fromRawLuaValue(table.get(k))
                val childVar = MobVariable(childName, rv)
                val childExpr = LuaExpr.child(expr, childName)
                list.add(childName, MobDebugValue(childVar, evaluator, frameIndex, childExpr))
            }
            node.addChildren(list, true)
        }, onError = {
            node.addChildren(XValueChildrenList.EMPTY, true)
        })
    }
}
