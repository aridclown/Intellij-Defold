package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldConstants.ELLIPSIS_VAR
import com.aridclown.intellij.defold.DefoldConstants.GLOBAL_VAR
import com.aridclown.intellij.defold.DefoldConstants.LOCALS_PAGE_SIZE
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.lua.isVarargName
import com.aridclown.intellij.defold.debugger.value.MobDebugValue
import com.aridclown.intellij.defold.debugger.value.MobDebugVarargValue
import com.aridclown.intellij.defold.debugger.value.MobRValue
import com.aridclown.intellij.defold.debugger.value.MobRValue.VarargPreview
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.aridclown.intellij.defold.debugger.value.MobVariable.Kind
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

    fun visibleLocals(): List<MobVariable> {
        val (varargs, regular) = variables.partition { it.name.isVarargName() }
        if (varargs.isEmpty()) return regular

        val inlineVarargs = MobVariable(
            name = ELLIPSIS_VAR,
            value = VarargPreview(varargs),
            expression = ELLIPSIS_VAR,
            kind = Kind.PARAMETER
        )

        return regular + inlineVarargs
    }

    private fun createChildrenList(): XValueChildrenList = XValueChildrenList().apply {
        val entries = buildEntries()
        addGlobalVars(GLOBAL_VAR)
        addVisibleEntries(entries.take(LOCALS_PAGE_SIZE))

        val remainingCount = (entries.size - LOCALS_PAGE_SIZE).coerceAtLeast(0)
        if (remainingCount > 0) {
            addMoreItemsNode(entries.drop(LOCALS_PAGE_SIZE), remainingCount)
        }
    }

    private fun XValueChildrenList.addGlobalVars(expression: String) {
        if (variables.any { it.name == GLOBAL_VAR }) return

        val variable = MobVariable(expression, MobRValue.GlobalVar(), expression)
        val debugValue = MobDebugValue(
            project, variable, evaluator, evaluationFrameIndex, sourcePosition
        )
        add(expression, debugValue)
    }

    private fun XValueChildrenList.addVisibleEntries(entries: List<FrameEntry>) = entries.forEach { entry ->
        when (entry) {
            is FrameEntry.Regular -> addRegularVariable(entry.variable)
            is FrameEntry.Varargs -> addVarargs(entry.variables)
        }
    }

    private fun XValueChildrenList.addRegularVariable(variable: MobVariable) {
        val debugValue = MobDebugValue(project, variable, evaluator, evaluationFrameIndex, sourcePosition)
        add(variable.name, debugValue)
    }

    private fun XValueChildrenList.addVarargs(varargs: List<MobVariable>) {
        val varargNode = MobDebugVarargValue(project, varargs, evaluator, evaluationFrameIndex, sourcePosition)
        add(ELLIPSIS_VAR, varargNode)
    }

    private fun XValueChildrenList.addMoreItemsNode(remainingEntries: List<FrameEntry>, remainingCount: Int) {
        val moreNode = MobMoreNode("($remainingCount more items)") { node ->
            val moreList = XValueChildrenList()
            moreList.addVisibleEntries(remainingEntries)
            node.addChildren(moreList, true)
        }
        add(moreNode)
    }

    private fun buildEntries(): List<FrameEntry> {
        val entries = mutableListOf<FrameEntry>()
        val varargs = mutableListOf<MobVariable>()
        var insertIndex = -1

        variables.forEach { variable ->
            if (variable.name.isVarargName()) {
                if (insertIndex == -1) insertIndex = entries.size
                varargs.add(variable)
            } else {
                entries.add(FrameEntry.Regular(variable))
            }
        }

        if (varargs.isNotEmpty()) {
            entries.add(insertIndex, FrameEntry.Varargs(varargs.toList()))
        }

        return entries
    }

    private sealed interface FrameEntry {
        data class Regular(val variable: MobVariable) : FrameEntry
        data class Varargs(val variables: List<MobVariable>) : FrameEntry
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
