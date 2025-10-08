package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.DefoldConstants.ELLIPSIS_VAR
import com.aridclown.intellij.defold.DefoldConstants.GLOBAL_VAR
import com.aridclown.intellij.defold.debugger.MobDebugProcess
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.lua.isVarargName
import com.aridclown.intellij.defold.debugger.value.MobRValue.*
import com.aridclown.intellij.defold.debugger.value.navigation.navigateToLocalDeclaration
import com.aridclown.intellij.defold.util.ResourceUtil
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XNumericValuePresentation
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation
import com.intellij.xdebugger.frame.presentation.XStringValuePresentation
import org.luaj.vm2.LuaTable

/**
 * Basic XNamedValue implementation showing the string representation of a variable.
 */
class MobDebugValue(
    project: Project,
    private val variable: MobVariable,
    evaluator: MobDebugEvaluator,
    frameIndex: Int?,
    framePosition: XSourcePosition? = null
) : BaseMobDebugValue(variable.name, project, evaluator, frameIndex, framePosition) {

    override fun doComputePresentation(node: XValueNode, place: XValuePlace) {
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

        node.setPresentation(variable.icon, presentation, v.hasChildren)
    }

    override fun computeChildren(node: XCompositeNode) {
        val baseExpr = variable.expression
        when (val rv = variable.value) {
            is Vector -> node.addVariables(rv.toMobVarList(baseExpr))
            is Matrix -> node.addVariables(rv.toMobVarList())
            is Url -> node.addVariables(rv.toMobVarList(baseExpr))
            is ScriptInstance -> node.addScriptInstanceChildren()
            is Table -> node.addTableChildren(rv.snapshot)
            else -> node.addEmptyChildren()
        }
    }

    override fun computeSourcePosition(xNavigable: XNavigatable) {
        fun String.sourceLookupName(): String = takeUnless { isVarargName() } ?: ELLIPSIS_VAR

        val frame = framePosition ?: return
        val lookupName = variable.name.sourceLookupName()
        navigateToLocalDeclaration(project, frame, lookupName, xNavigable)
    }

    override fun getModifier(): XValueModifier? {
        if (frameIndex == null || isNotModifiable()) return null

        // Get the debug process from the current session
        val currentSession = XDebuggerManager.getInstance(project).currentSession
        val debugProcess = currentSession?.debugProcess as? MobDebugProcess

        return debugProcess?.let {
            MobDebugValueModifier(evaluator, frameIndex, variable, it)
        }
    }

    private fun isNotModifiable(): Boolean =
        variable.name.isVarargName() ||
                variable.name == GLOBAL_VAR ||
                variable.value::class in setOf(
            Func::class, Thread::class, Userdata::class, Matrix::class, ScriptInstance::class
        )

    private fun XCompositeNode.addScriptInstanceChildren() {
        val baseExpr = variable.expression
        if (frameIndex == null || baseExpr.isBlank()) {
            addEmptyChildren()
            return
        }

        evaluator.evaluateExpr(
            frameIndex,
            expr = scriptInstanceTableExpr(baseExpr),
            onSuccess = { value ->
                when {
                    value.istable() -> addPaginatedTableChildren(value.checktable())
                    else -> addEmptyChildren()
                }
            },
            onError = { addEmptyChildren() })
    }


    private fun XCompositeNode.addTableChildren(snapshot: LuaTable?) {
        fun addSnapshotOrEmpty() = when {
            snapshot == null -> addEmptyChildren()
            else -> addPaginatedTableChildren(snapshot)
        }

        val baseExpr = variable.expression
        if (frameIndex == null || baseExpr.isBlank()) {
            addSnapshotOrEmpty()
            return
        }

        evaluator.evaluateExpr(
            frameIndex, baseExpr, onSuccess = { value ->
                when {
                    value.istable() -> addPaginatedTableChildren(value.checktable())
                    else -> addSnapshotOrEmpty()
                }
            },
            onError = { addSnapshotOrEmpty() }
        )
    }

    private fun XCompositeNode.addPaginatedTableChildren(table: LuaTable) {
        val sortedKeys = TableChildrenPager.sortedKeys(table)
        addPaginatedVariables(sortedKeys.size) { from, to ->
            TableChildrenPager
                .buildSlice(variable.expression, table, sortedKeys, from, to)
                .map { MobVariable(it.name, it.rvalue, it.expr) }
        }
    }

    private fun scriptInstanceTableExpr(baseExpr: String): String = ResourceUtil.loadAndProcessLuaScript(
        resourcePath = "debugger/load_scriptinstance.lua",
        compactWhitespace = true,
        "{{BASE_EXPR}}" to baseExpr,
    )
}
