package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldConstants.TABLE_PAGE_SIZE
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.lua.LuaExprUtil
import com.aridclown.intellij.defold.debugger.value.MobRValue
import com.aridclown.intellij.defold.debugger.value.MobRValue.Num
import com.aridclown.intellij.defold.debugger.value.MobRValue.Str
import com.aridclown.intellij.defold.debugger.value.MobRValue.Vector
import com.aridclown.intellij.defold.debugger.value.MobRValue.Quat
import com.aridclown.intellij.defold.debugger.value.MobRValue.Matrix
import com.aridclown.intellij.defold.debugger.value.MobRValue.Url
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
        val rv = variable.value

        if (rv is Vector || rv is Quat) {
            val names = listOf("x", "y", "z", "w")
            val comps = when (rv) {
                is Vector -> rv.components
                is Quat -> rv.components
                else -> emptyList()
            }
            val list = XValueChildrenList()
            for (i in comps.indices) {
                val name = names[i]
                val num = Num(comps[i].toString())
                val childVar = MobVariable(name, num)
                list.add(name, MobDebugValue(childVar, evaluator, frameIndex, "$expr.$name"))
            }
            node.addChildren(list, true)
            return
        }

        if (rv is Matrix) {
            val list = XValueChildrenList()
            for (i in rv.rows.indices) {
                val rowName = "row${i + 1}"
                val rowVector = Vector("", rv.rows[i])
                val childVar = MobVariable(rowName, rowVector)
                list.add(rowName, MobDebugValue(childVar, evaluator, frameIndex, ""))
            }
            node.addChildren(list, true)
            return
        }

        if (rv is Url) {
            val list = XValueChildrenList()
            val socketVar = MobVariable("socket", Str(rv.socket))
            list.add("socket", MobDebugValue(socketVar, evaluator, frameIndex, "$expr.socket"))
            rv.path?.let {
                val pathVar = MobVariable("path", Str(it))
                list.add("path", MobDebugValue(pathVar, evaluator, frameIndex, "$expr.path"))
            }
            rv.fragment?.let {
                val fragVar = MobVariable("fragment", Str(it))
                list.add("fragment", MobDebugValue(fragVar, evaluator, frameIndex, "$expr.fragment"))
            }
            node.addChildren(list, true)
            return
        }

        if (rv !is MobRValue.Table) {
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
                    val childExpr = LuaExprUtil.child(expr, childName)
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
