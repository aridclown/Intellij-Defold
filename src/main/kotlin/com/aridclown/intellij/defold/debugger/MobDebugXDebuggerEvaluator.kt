package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.lua.LuaExprUtil
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.tang.intellij.lua.psi.*

/**
 * XDebugger evaluator used for hover/quick evaluate. Uses PSI to find a reasonable
 * expression range (identifier with optional member chain) at caret and evaluates it
 * in the frame using MobDebugEvaluator.
 */
class MobDebugXDebuggerEvaluator(
    private val evaluator: MobDebugEvaluator,
    private val frameIndex: Int,
    private val framePosition: XSourcePosition?,
    @Suppress("unused") private val allowedRoots: Set<String>
) : XDebuggerEvaluator() {

    private val reserved = setOf(
        "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if", "in",
        "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while"
    )

    override fun getExpressionRangeAtOffset(
        project: Project,
        document: Document,
        offset: Int,
        sideEffectsAllowed: Boolean
    ): TextRange? {
        return ReadAction.compute<TextRange?, Throwable> {
            val pdm = PsiDocumentManager.getInstance(project)
            val file = pdm.getPsiFile(document) ?: return@compute null
            // Only evaluate within the same file as the paused frame
            val frameFile = framePosition?.file
            if (frameFile != null && file.virtualFile != frameFile) return@compute null

            val leaf = file.findElementAt(offset) ?: return@compute null

            // Ensure hover is within the same function as the paused frame
            val hoverFunc = PsiTreeUtil.getParentOfType(
                leaf,
                LuaFuncDef::class.java,
                LuaLocalFuncDef::class.java,
                LuaClosureExpr::class.java
            )
            // Prefer the exact offset of the paused frame if available; fall back to line start.
            val frameOffset = framePosition?.let { pos ->
                val off = pos.offset
                if (off >= 0) off else document.getLineStartOffset(pos.line)
            } ?: return@compute null
            val frameLeaf = file.findElementAt(frameOffset) ?: return@compute null
            val frameFunc = PsiTreeUtil.getParentOfType(
                frameLeaf,
                LuaFuncDef::class.java,
                LuaLocalFuncDef::class.java,
                LuaClosureExpr::class.java
            )
            if (hoverFunc != frameFunc) return@compute null

            // EmmyLua-like range detection: pick the enclosing non-call expression at caret.
            val expr = PsiTreeUtil.findElementOfClassAtOffset(
                file,
                offset,
                LuaExpr::class.java,
                false
            )
            val range: TextRange? = when (expr) {
                is LuaCallExpr, is LuaClosureExpr, is LuaLiteralExpr -> null
                else -> expr?.textRange
            }
            if (range == null) return@compute null

            val text = document.getText(range).trim()
            if (text in reserved) return@compute null
            val pattern = Regex("[A-Za-z_][A-Za-z0-9_]*(?:\u0020*[\u002E\u003A]\u0020*[A-Za-z_][A-Za-z0-9_]*)*")
            if (!pattern.matches(text)) return@compute null

            // Don't restrict by local roots; let the debugger/runtime resolve globals.
            val root = text.substringBefore('.').substringBefore(':').trim()
            if (root in reserved) return@compute null
            range
        }
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
            val rv = com.aridclown.intellij.defold.debugger.value.MobRValue.fromRawLuaValue(value)
            val v = MobVariable(expr, rv)
            callback.evaluated(MobDebugValue(v, evaluator, frameIndex, LuaExprUtil.child("", expr).trimStart('.')))
        }, onError = { err ->
            callback.errorOccurred(err)
        })
    }

}
