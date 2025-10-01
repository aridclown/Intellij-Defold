package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.DefoldConstants.ELLIPSIS_VAR
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.value.MobRValue.VarargPreview
import com.aridclown.intellij.defold.debugger.value.navigation.navigateToLocalDeclaration
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation

class MobDebugVarargValue(
    project: Project,
    private val varargs: List<MobVariable>,
    evaluator: MobDebugEvaluator,
    frameIndex: Int?,
    framePosition: XSourcePosition?
) : BaseMobDebugValue(ELLIPSIS_VAR, project, evaluator, frameIndex, framePosition) {

    private val varargPreview = VarargPreview(varargs)

    override fun doComputePresentation(node: XValueNode, place: XValuePlace) {
        node.setPresentation(
            varargPreview.icon,
            XRegularValuePresentation(varargPreview.preview, varargPreview.typeLabel),
            true
        )
    }

    override fun computeSourcePosition(xNavigable: XNavigatable) {
        val frame = framePosition ?: return
        navigateToLocalDeclaration(project, frame, ELLIPSIS_VAR, xNavigable)
    }

    override fun computeChildren(node: XCompositeNode) {
        node.addPaginatedVariables(varargs.size) { from, to ->
            if (from >= to) emptyList() else varargs.subList(from, to)
        }
    }
}
