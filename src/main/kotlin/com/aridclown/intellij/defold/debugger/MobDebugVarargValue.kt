package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldConstants.TABLE_PAGE_SIZE
import com.aridclown.intellij.defold.DefoldConstants.VARARG_DISPLAY_NAME
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation

class MobDebugVarargValue(
    private val project: Project,
    private val varargs: List<MobVariable>,
    private val evaluator: MobDebugEvaluator,
    private val frameIndex: Int?,
    private val framePosition: XSourcePosition?
) : XNamedValue(VARARG_DISPLAY_NAME) {

    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        val preview = when (varargs.size) {
            1 -> "1 value"
            else -> "${varargs.size} values"
        }
        node.setPresentation(AllIcons.Json.Array, XRegularValuePresentation(preview, "vararg"), true)
    }

    override fun computeChildren(node: XCompositeNode) {
        if (varargs.isEmpty()) {
            node.addChildren(XValueChildrenList.EMPTY, true)
            return
        }
        val to = TABLE_PAGE_SIZE.coerceAtMost(varargs.size)
        addSlice(0, to, node)
    }

    private fun addSlice(from: Int, to: Int, container: XCompositeNode) {
        val list = XValueChildrenList()
        val slice = varargs.subList(from, to)
        for (variable in slice) {
            val value = MobDebugValue(project, variable, evaluator, frameIndex, framePosition)
            list.add(variable.name, value)
        }

        val remaining = varargs.size - to
        if (remaining > 0) {
            list.add(MobMoreNode("($remaining more items)") { nextNode ->
                val nextTo = (to + TABLE_PAGE_SIZE).coerceAtMost(varargs.size)
                addSlice(to, nextTo, nextNode)
            })
        }
        container.addChildren(list, true)
    }
}
