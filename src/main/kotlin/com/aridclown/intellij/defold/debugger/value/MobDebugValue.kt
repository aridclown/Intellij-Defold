package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.DefoldConstants.TABLE_PAGE_SIZE
import com.aridclown.intellij.defold.debugger.MobMoreNode
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.lua.LuaExprUtil
import com.aridclown.intellij.defold.debugger.value.MobRValue.*
import com.aridclown.intellij.defold.util.ResourceUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.*
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
    private val project: Project,
    private val variable: MobVariable,
    private val evaluator: MobDebugEvaluator,
    private val frameIndex: Int?,
    private val framePosition: XSourcePosition? = null
) : XNamedValue(variable.name) {

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
        val baseExpr = variable.expression
        when (val rv = variable.value) {
            is Vector, is Quat -> {
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
                    val childVar = MobVariable(name, num, LuaExprUtil.child(baseExpr, name))
                    list.add(name, MobDebugValue(project, childVar, evaluator, frameIndex, framePosition))
                }
                node.addChildren(list, true)
            }

            is Matrix -> {
                val list = XValueChildrenList()
                for (i in rv.rows.indices) {
                    val rowName = "row${i + 1}"
                    val rowVector = Vector("", rv.rows[i])
                    val childVar = MobVariable(rowName, rowVector, "")
                    list.add(rowName, MobDebugValue(project, childVar, evaluator, frameIndex, framePosition))
                }
                node.addChildren(list, true)
            }

            is Url -> {
                val list = XValueChildrenList()
                val socketVar = MobVariable("socket", Str(rv.socket), LuaExprUtil.child(baseExpr, "socket"))
                list.add("socket", MobDebugValue(project, socketVar, evaluator, frameIndex, framePosition))
                rv.path?.let {
                    val pathVar = MobVariable("path", Str(it), LuaExprUtil.child(baseExpr, "path"))
                    list.add("path", MobDebugValue(project, pathVar, evaluator, frameIndex, framePosition))
                }
                rv.fragment?.let {
                    val fragVar = MobVariable("fragment", Str(it), LuaExprUtil.child(baseExpr, "fragment"))
                    list.add("fragment", MobDebugValue(project, fragVar, evaluator, frameIndex, framePosition))
                }
                node.addChildren(list, true)
            }

            is ScriptInstance -> loadScriptInstanceChildren(node)
            is Table -> loadTableChildren(node, rv.snapshot)
            else -> addEmptyChildren(node)
        }
    }

    private fun loadScriptInstanceChildren(node: XCompositeNode) {
        val baseExpr = variable.expression
        if (frameIndex == null || baseExpr.isBlank()) {
            addEmptyChildren(node)
            return
        }

        evaluator.evaluateExpr(
            frameIndex,
            expr = scriptInstanceTableExpr(baseExpr),
            onSuccess = { value ->
                when {
                    value.istable() -> addTableChildren(node, value.checktable())
                    else -> addEmptyChildren(node)
                }
            },
            onError = { addEmptyChildren(node) })
    }

    private fun loadTableChildren(node: XCompositeNode, snapshot: LuaTable?) {
        fun addSnapshotOrEmpty() = if (snapshot != null) addTableChildren(node, snapshot) else addEmptyChildren(node)

        val baseExpr = variable.expression
        if (frameIndex == null || baseExpr.isBlank()) {
            addSnapshotOrEmpty()
            return
        }

        evaluator.evaluateExpr(frameIndex, baseExpr, onSuccess = { value ->
            if (value.istable()) {
                addTableChildren(node, value.checktable())
            } else {
                addSnapshotOrEmpty()
            }
        }, onError = {
            addSnapshotOrEmpty()
        })
    }

    private fun addTableChildren(node: XCompositeNode, table: LuaTable) {
        val sortedKeys = TableChildrenPager.sortedKeys(table)

        fun addSlice(from: Int, to: Int, container: XCompositeNode) {
            val list = XValueChildrenList()
            val entries = TableChildrenPager.buildSlice(variable.expression, table, sortedKeys, from, to)
            for (e in entries) {
                val childVar = MobVariable(e.name, e.rvalue, e.expr)
                list.add(e.name, MobDebugValue(project, childVar, evaluator, frameIndex, framePosition))
            }
            val remaining = TableChildrenPager.remaining(sortedKeys, to)
            if (remaining > 0) {
                list.add(MobMoreNode("($remaining more items)") { nextNode ->
                    val nextTo = (to + TABLE_PAGE_SIZE).coerceAtMost(sortedKeys.size)
                    addSlice(to, nextTo, nextNode)
                })
            }
            container.addChildren(list, true)
        }

        val to = TABLE_PAGE_SIZE.coerceAtMost(sortedKeys.size)
        addSlice(0, to, node)
    }

    private fun addEmptyChildren(node: XCompositeNode) {
        node.addChildren(XValueChildrenList.EMPTY, true)
    }

    private fun scriptInstanceTableExpr(baseExpr: String): String = ResourceUtil.loadAndProcessLuaScript(
        resourcePath = "debugger/get_instance_data.lua",
        compactWhitespace = true,
        "BASE_EXPR" to baseExpr,
    )

    override fun computeSourcePosition(xNavigable: XNavigatable) {
        if (framePosition != null) {
            ReadAction.run<Throwable> {
                val file = framePosition.file
                val psiFile = PsiManager.getInstance(project).findFile(file)
                val editor = FileEditorManager.getInstance(project).getSelectedEditor(file)

                if (psiFile != null && editor is TextEditor) {
                    val document = editor.editor.document
                    val lineEndOffset = document.getLineStartOffset(framePosition.line)
                    val element = psiFile.findElementAt(lineEndOffset) ?: return@run
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
}
