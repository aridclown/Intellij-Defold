package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.lua.LuaExprUtil.child
import com.aridclown.intellij.defold.debugger.value.MobRValue.*
import com.aridclown.intellij.defold.util.ResourceUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XNumericValuePresentation
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation
import com.intellij.xdebugger.frame.presentation.XStringValuePresentation
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.tang.intellij.lua.psi.LuaDeclarationTree
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

        node.setPresentation(v.icon, presentation, v.hasChildren)
    }

    override fun computeChildren(node: XCompositeNode) {
        val baseExpr = variable.expression
        when (val rv = variable.value) {
            is Vector, is Quat -> node.loadVectorOrQuatChildren(baseExpr, rv)
            is Matrix -> node.loadMatrixChildren(rv)
            is Url -> node.loadUrlChildren(baseExpr, rv)
            is ScriptInstance -> node.loadScriptInstanceChildren()
            is Table -> node.loadTableChildren(rv.snapshot)
            else -> node.addEmptyChildren()
        }
    }

    override fun computeSourcePosition(xNavigable: XNavigatable) {
        if (framePosition != null) {
            runReadAction {
                val file = framePosition.file
                val psiFile = PsiManager.getInstance(project).findFile(file)
                val editor = FileEditorManager.getInstance(project).getSelectedEditor(file)

                if (psiFile != null && editor is TextEditor) {
                    val document = editor.editor.document
                    val lineEndOffset = document.getLineStartOffset(framePosition.line)
                    val element = psiFile.findElementAt(lineEndOffset) ?: return@runReadAction
                    LuaDeclarationTree.get(psiFile).walkUpLocal(element) {
                        if (name == it.name) {
                            val position = XSourcePositionImpl.createByElement(it.psi)
                            xNavigable.setSourcePosition(position)
                            return@walkUpLocal false
                        }
                        true
                    }
                }
            }
        }
    }

    private fun XCompositeNode.loadVectorOrQuatChildren(baseExpr: String, value: MobRValue) {
        val components = when (value) {
            is Vector -> value.components
            is Quat -> value.components
            else -> emptyList()
        }

        if (components.isEmpty()) return

        val vars = listOf("x", "y", "z", "w")
            .take(components.size)
            .mapIndexed { index, name ->
                val num = Num(components[index].toString())
                MobVariable(name, num, child(baseExpr, name))
            }

        addVariables(vars)
    }

    private fun XCompositeNode.loadMatrixChildren(value: Matrix) = value.rows
        .mapIndexed { index, row ->
            val rowName = "row${index + 1}"
            MobVariable(rowName, Vector("", row), "")
        }
        .also { addVariables(it) }

    private fun XCompositeNode.loadUrlChildren(baseExpr: String, value: Url) = buildList {
        add(MobVariable("socket", Str(value.socket), child(baseExpr, "socket")))
        value.path?.let {
            add(MobVariable("path", Str(it), child(baseExpr, "path")))
        }
        value.fragment?.let {
            add(MobVariable("fragment", Str(it), child(baseExpr, "fragment")))
        }
    }.also { addVariables(it) }

    private fun XCompositeNode.loadScriptInstanceChildren() {
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
                    value.istable() -> addTableChildren(value.checktable())
                    else -> addEmptyChildren()
                }
            },
            onError = { addEmptyChildren() })
    }

    private fun XCompositeNode.loadTableChildren(snapshot: LuaTable?) {
        fun addSnapshotOrEmpty() = when {
            snapshot == null -> addEmptyChildren()
            else -> addTableChildren(snapshot)
        }

        val baseExpr = variable.expression
        if (frameIndex == null || baseExpr.isBlank()) {
            addSnapshotOrEmpty()
            return
        }

        evaluator.evaluateExpr(
            frameIndex, baseExpr, onSuccess = { value ->
                when {
                    value.istable() -> addTableChildren(value.checktable())
                    else -> addSnapshotOrEmpty()
                }
            },
            onError = { addSnapshotOrEmpty() }
        )
    }

    private fun XCompositeNode.addTableChildren(table: LuaTable) {
        val sortedKeys = TableChildrenPager.sortedKeys(table)
        addPaginatedVariables(sortedKeys.size) { from, to ->
            TableChildrenPager
                .buildSlice(variable.expression, table, sortedKeys, from, to)
                .map { MobVariable(it.name, it.rvalue, it.expr) }
        }
    }

    private fun scriptInstanceTableExpr(baseExpr: String): String = ResourceUtil.loadAndProcessLuaScript(
        resourcePath = "debugger/get_instance_data.lua",
        compactWhitespace = true,
        "BASE_EXPR" to baseExpr,
    )
}