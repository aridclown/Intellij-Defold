package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.lua.isVarargs
import com.aridclown.intellij.defold.debugger.value.MobDebugValue
import com.aridclown.intellij.defold.debugger.value.MobDebugVarargValue
import com.aridclown.intellij.defold.debugger.value.MobRValue
import com.aridclown.intellij.defold.debugger.value.MobRValue.Companion.createVarargs
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.tang.intellij.lua.psi.*

/**
 * XDebugger evaluator used for hover/quick evaluating. Uses PSI to find a reasonable
 * expression range (identifier with an optional member chain) at caret and evaluates it
 * in the frame using MobDebugEvaluator.
 */
class MobDebugXDebuggerEvaluator(
    private val project: Project,
    private val evaluator: MobDebugEvaluator,
    private val frameIndex: Int,
    private val framePosition: XSourcePosition?
) : XDebuggerEvaluator() {

    override fun getExpressionRangeAtOffset(
        project: Project,
        document: Document,
        offset: Int,
        sideEffectsAllowed: Boolean
    ): TextRange? = ReadAction.compute<TextRange?, Throwable> {
        var currentRange: TextRange? = null
        val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return@compute null

        // Only evaluate within the same file as the paused frame
        val frameFile = framePosition?.file
        if (frameFile != null && file.virtualFile != frameFile) return@compute null

        // Ensure hover is within the same function as the paused frame
        val leaf = file.findElementAt(offset) ?: return@compute null
        val hoverFunc = PsiTreeUtil.getParentOfType(
            leaf,
            LuaFuncDef::class.java,
            LuaLocalFuncDef::class.java,
            LuaClosureExpr::class.java
        )

        val frameOffset = framePosition?.let { pos ->
            val off = pos.offset
            if (off >= 0) off else document.getLineStartOffset(pos.line)
        }
        if (frameOffset != null) {
            val frameLeaf = file.findElementAt(frameOffset)
            val frameFunc = frameLeaf?.let {
                PsiTreeUtil.getParentOfType(
                    it,
                    LuaFuncDef::class.java,
                    LuaLocalFuncDef::class.java,
                    LuaClosureExpr::class.java
                )
            }
            if (hoverFunc != frameFunc) return@compute null
        }

        // Find the nearest identifier or member chain at the caret
        val el = file.findElementAt(offset)
        if (el != null && el.node.elementType == LuaTypes.ID) {
            when (val parent = el.parent) {
                is LuaFuncDef, is LuaLocalFuncDef -> currentRange = el.textRange
                is LuaClassMethodName, is PsiNameIdentifierOwner -> currentRange = parent.textRange
            }
        }

        // Check if the leaf is a varargs token and handle based on context
        if (currentRange == null && leaf.node.elementType == LuaTypes.ELLIPSIS) {
            when (val parent = leaf.parent) {
                is LuaLiteralExpr -> {
                    // Varargs used as an expression (e.g., in print(...) or return ...)
                    currentRange = parent.textRange
                }

                is LuaFuncBody -> {
                    // Varargs in function parameter declaration
                    // Check if we're actually paused inside the function (not at the declaration line)
                    val isPausedInsideFunction = frameOffset != null && frameOffset != offset
                    if (isPausedInsideFunction) {
                        // We're inside the function, so varargs has a value - allow evaluation
                        currentRange = leaf.textRange
                    } else {
                        // We're at the function declaration itself - can't evaluate
                        return@compute null
                    }
                }
            }
        }

        // Fall back to the exact offset if no identifier was found
        if (currentRange == null) {
            val expr = PsiTreeUtil.findElementOfClassAtOffset(file, offset, LuaExpr::class.java, false)
            currentRange = when (expr) {
                is LuaCallExpr, is LuaClosureExpr -> null
                is LuaLiteralExpr if expr.kind != LuaLiteralKind.Varargs -> null
                else -> expr?.textRange
            }
        }

        // Filter out reserved words and expressions that can't be evaluated
        if (currentRange != null) {
            val text = document.getText(currentRange).trim()
            val pattern = Regex(
                "([A-Za-z_][A-Za-z0-9_]*(?:\\u0020*[\\u002E\\u003A]\\u0020*[A-Za-z_][A-Za-z0-9_]*)*|\\.\\.\\.)"
            )
            if (!pattern.matches(text)) currentRange = null
        }
        currentRange
    }

    override fun evaluate(expression: String, callback: XEvaluationCallback, expressionPosition: XSourcePosition?) {
        var expr = expression.trim()
        // Normalize method sugar a:b to a.b when not a call, mirroring EmmyLua
        if (!expr.endsWith(')')) {
            val lastDot = expr.lastIndexOf('.')
            val lastColon = expr.lastIndexOf(':')
            if (lastColon > lastDot) expr = expr.replaceRange(lastColon, lastColon + 1, ".")
        }

        evaluator.evaluateExpr(frameIndex, expr, onSuccess = { value ->
            if (expr.isVarargs() && value.istable()) {
                val varargs = createVarargs(value.checktable())
                callback.evaluated(MobDebugVarargValue(project, varargs, evaluator, frameIndex, framePosition))
                return@evaluateExpr
            }

            // Regular evaluation
            val rv = MobRValue.fromRawLuaValue(expr, value)
            val variable = MobVariable(expr, rv)
            callback.evaluated(MobDebugValue(project, variable, evaluator, frameIndex, framePosition))
        }, onError = { err ->
            callback.errorOccurred(err)
        })
    }

}
