package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldConstants.GLOBAL_TABLE_VAR
import com.aridclown.intellij.defold.DefoldConstants.LOCALS_PAGE_SIZE
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.value.MobRValue
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.impl.XSourcePositionImpl

/**
 * Single Lua stack frame for MobDebug.
 */
class MobDebugStackFrame(
    private val project: Project,
    private val filePath: String?,
    private val line: Int,
    private val variables: List<MobVariable> = emptyList(),
    private val evaluator: MobDebugEvaluator,
    private val evaluationFrameIndex: Int?
) : XStackFrame() {

    fun visibleLocals(): List<MobVariable> = variables

    override fun getSourcePosition(): XSourcePosition? {
        val path = filePath ?: return null
        val vFile = LocalFileSystem.getInstance().findFileByPath(path) ?: return null
        return XSourcePositionImpl.create(vFile, line - 1)
    }

    override fun computeChildren(node: XCompositeNode) {
        val childrenList = createChildrenList()
        node.addChildren(childrenList, true)
    }

    override fun getEvaluator(): XDebuggerEvaluator? = evaluationFrameIndex?.let { frameIdx ->
        MobDebugXDebuggerEvaluator(
            project,
            evaluator,
            frameIdx,
            framePosition = sourcePosition
        )
    }

    private fun createChildrenList(): XValueChildrenList = XValueChildrenList().apply {
        addGlobalVars(GLOBAL_TABLE_VAR)
        addVisibleVars(variables = variables.take(LOCALS_PAGE_SIZE))

        val remainingCount = variables.size - LOCALS_PAGE_SIZE
        if (remainingCount > 0) {
            addMoreItemsNode(remainingCount)
        }
    }

    private fun XValueChildrenList.addGlobalVars(expression: String) {
        if (variables.any { it.name == GLOBAL_TABLE_VAR }) return

        val variable = MobVariable(expression, MobRValue.Table(), expression)
        val debugValue = MobDebugValue(
            project, variable, evaluator, evaluationFrameIndex, sourcePosition
        )
        add(expression, debugValue)
    }

    private fun XValueChildrenList.addVisibleVars(variables: List<MobVariable>) = variables.forEach { variable ->
        val debugValue = MobDebugValue(
            project, variable, evaluator, evaluationFrameIndex, sourcePosition
        )
        add(variable.name, debugValue)
    }

    private fun XValueChildrenList.addMoreItemsNode(remainingCount: Int) {
        val moreNode = MobMoreNode("($remainingCount more items)") { node ->
            val remainingVariables = variables.drop(LOCALS_PAGE_SIZE)
            val moreList = XValueChildrenList()
            moreList.addVisibleVars(remainingVariables)
            node.addChildren(moreList, true)
        }
        add(moreNode)
    }
}

class MobMoreNode(
    private val displayText: String,
    private val childrenLoader: (XCompositeNode) -> Unit
) : XNamedValue("Show more") {

    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        node.setPresentation(null, null, displayText, true)
    }

    override fun computeChildren(node: XCompositeNode) {
        childrenLoader(node)
    }
}
