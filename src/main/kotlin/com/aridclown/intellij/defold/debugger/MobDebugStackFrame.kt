package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldConstants.LOCALS_PAGE_SIZE
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.impl.XSourcePositionImpl

/**
 * Single Lua stack frame for MobDebug.
 */
class MobDebugStackFrame(
    private val filePath: String?,
    private val line: Int,
    private val variables: List<MobVariable> = emptyList(),
    private val evaluator: MobDebugEvaluator,
    private val frameIndex: Int
) : XStackFrame() {

    override fun getSourcePosition(): XSourcePosition? {
        val path = filePath ?: return null
        val vFile = LocalFileSystem.getInstance().findFileByPath(path) ?: return null
        return XSourcePositionImpl.create(vFile, line - 1)
    }

    override fun computeChildren(node: XCompositeNode) {
        val childrenList = createChildrenList()
        node.addChildren(childrenList, true)
    }

    private fun createChildrenList(): XValueChildrenList {
        val list = XValueChildrenList()

        val visibleVariables = variables.take(LOCALS_PAGE_SIZE)
        addVariablesToList(list, visibleVariables)

        val remainingCount = variables.size - LOCALS_PAGE_SIZE
        if (remainingCount > 0) {
            addMoreItemsNode(list, remainingCount)
        }

        return list
    }

    private fun addVariablesToList(list: XValueChildrenList, variables: List<MobVariable>) {
        variables.forEach { variable ->
            val debugValue = MobDebugValue(variable, evaluator, frameIndex, variable.name)
            list.add(variable.name, debugValue)
        }
    }

    private fun addMoreItemsNode(list: XValueChildrenList, remainingCount: Int) {
        val moreNode = MobMoreNode("($remainingCount more items)") { node ->
            val remainingVariables = variables.drop(LOCALS_PAGE_SIZE)
            val moreList = XValueChildrenList()
            addVariablesToList(moreList, remainingVariables)
            node.addChildren(moreList, true)
        }
        list.add(moreNode)
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
