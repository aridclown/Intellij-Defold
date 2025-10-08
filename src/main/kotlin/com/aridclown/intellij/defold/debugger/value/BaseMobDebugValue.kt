package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.debugger.MobMoreNode
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.XValueNode

/**
 * Base value node with helpers for creating `MobDebugValue` children and shared pagination wiring.
 */
abstract class BaseMobDebugValue(
    name: String,
    protected val project: Project,
    protected val evaluator: MobDebugEvaluator,
    protected val frameIndex: Int?,
    protected val framePosition: XSourcePosition?
) : XNamedValue(name) {

    protected fun MobVariable.childValue(): MobDebugValue =
        MobDebugValue(project, this, evaluator, frameIndex, framePosition)

    protected fun XCompositeNode.addVariables(children: List<MobVariable>) {
        if (children.isEmpty()) {
            addEmptyChildren()
            return
        }

        val list = XValueChildrenList()
        for (child in children) {
            list.add(child.name, child.childValue())
        }
        addChildren(list, true)
    }

    protected fun XCompositeNode.addEmptyChildren() {
        addChildren(XValueChildrenList.EMPTY, true)
    }

    protected fun XCompositeNode.addPaginatedVariables(
        totalSize: Int,
        sliceBuilder: (Int, Int) -> List<MobVariable>
    ) {
        val firstRange = MobValuePagination.range(totalSize, 0) ?: run {
            addEmptyChildren()
            return
        }

        addPaginatedSlice(firstRange, totalSize, sliceBuilder, this)
    }

    private fun XCompositeNode.addPaginatedSlice(
        range: MobValuePagination.Range,
        totalSize: Int,
        sliceBuilder: (Int, Int) -> List<MobVariable>,
        container: XCompositeNode
    ) {
        val (from, to, remaining) = range
        val list = XValueChildrenList()
        val slice = sliceBuilder(from, to)
        for (variable in slice) {
            list.add(variable.name, variable.childValue())
        }
        if (remaining > 0) {
            list.add(MobMoreNode("($remaining more items)") { nextNode ->
                val nextRange = MobValuePagination.range(totalSize, to) ?: return@MobMoreNode
                addPaginatedSlice(nextRange, totalSize, sliceBuilder, nextNode)
            })
        }
        container.addChildren(list, true)
    }

    // Ensures subclasses can still override presentation while reusing helpers.
    final override fun computePresentation(node: XValueNode, place: XValuePlace) {
        doComputePresentation(node, place)
    }

    protected abstract fun doComputePresentation(node: XValueNode, place: XValuePlace)
}
