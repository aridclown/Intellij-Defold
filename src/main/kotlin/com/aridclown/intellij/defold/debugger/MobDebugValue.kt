package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldConstants.TABLE_PAGE_SIZE
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
            is Str -> object : XStringValuePresentation(v.content) {
                override fun getType() = v.typeLabel
            }

            is Num -> object : XNumericValuePresentation(v.content) {
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
            val keys = table.keys().toList()
            val sorted = keys.sortedWith(compareBy({ !it.isnumber() }, { it.tojstring() }))
            fun addSlice(from: Int, to: Int, container: XCompositeNode) {
                val list = XValueChildrenList()
                for (i in from until to) {
                    val k = sorted[i]
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
                val remaining = sorted.size - to
                if (remaining > 0) {
                    list.add(MobMoreNode("($remaining more items)") { nextNode ->
                        val nextTo = (to + TABLE_PAGE_SIZE).coerceAtMost(sorted.size)
                        addSlice(to, nextTo, nextNode)
                    })
                }
                container.addChildren(list, true)
            }

            val to = TABLE_PAGE_SIZE.coerceAtMost(sorted.size)
            addSlice(0, to, node)
        }, onError = {
            node.addChildren(XValueChildrenList.EMPTY, true)
        })
    }
}
