package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.DefoldConstants.VARARG_DISPLAY_NAME
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation

class MobDebugVarargValue(
    project: Project,
    private val varargs: List<MobVariable>,
    evaluator: MobDebugEvaluator,
    frameIndex: Int?,
    framePosition: XSourcePosition?
) : BaseMobDebugValue(VARARG_DISPLAY_NAME, project, evaluator, frameIndex, framePosition) {

    override fun doComputePresentation(node: XValueNode, place: XValuePlace) {
        val preview = when (varargs.size) {
            1 -> "1 value"
            else -> "${varargs.size} values"
        }
        node.setPresentation(AllIcons.Json.Array, XRegularValuePresentation(preview, "vararg"), true)
    }

    override fun computeChildren(node: XCompositeNode) {
        node.addPaginatedVariables(varargs.size) { from, to ->
            if (from >= to) emptyList() else varargs.subList(from, to)
        }
    }
}
