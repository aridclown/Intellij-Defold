package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.lua.LuaExpr
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator

/**
 * XDebugger evaluator used for hover/quick evaluate. Uses PSI to find a reasonable
 * expression range (identifier with optional member chain) at caret and evaluates it
 * in the frame using MobDebugEvaluator.
 */
class MobDebugXDebuggerEvaluator(
    private val evaluator: MobDebugEvaluator,
    private val frameIndex: Int,
    private val framePosition: XSourcePosition?,
    private val allowedRoots: Set<String>
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
            // Restrict to the same function scope: compare nearest 'function' keyword before offsets
            val hoverFuncStart = nearestFunctionStart(document, offset)
            val frameOffset = framePosition?.line?.let { document.getLineStartOffset(it) }
            val frameFuncStart = frameOffset?.let { nearestFunctionStart(document, it) }
            if (hoverFuncStart != frameFuncStart) return@compute null
            val leaf = file.findElementAt(offset) ?: return@compute null

            // Expand to identifier under caret if inside whitespace
            val base = when {
                leaf.text.isBlank() && offset > 0 -> file.findElementAt(offset - 1) ?: leaf
                else -> leaf
            }

            // Build a simple member chain: identifier ('.'|':') identifier ...
            val start = expandLeftToIdentifier(base) ?: return@compute null
            val end = expandRightChain(base) ?: return@compute null
            if (end.textRange.startOffset < start.textRange.startOffset) return@compute null

            val range = TextRange(start.textRange.startOffset, end.textRange.endOffset)
            // Validate the text matches an identifier/member chain pattern; otherwise, skip (avoid strings/comments/numbers)
            val text = document.getText(range).trim()
            if (text in reserved) return@compute null
            val pattern = Regex("[A-Za-z_][A-Za-z0-9_]*(?:\u0020*[\u002E\u003A]\u0020*[A-Za-z_][A-Za-z0-9_]*)*")
            if (!pattern.matches(text)) return@compute null

            // Only evaluate if the root identifier is a known local/upvalue/param name
            val root = text.substringBefore('.')
                .substringBefore(':')
                .trim()
            if (root in reserved) return@compute null
            if (!allowedRoots.contains(root)) return@compute null
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
            callback.evaluated(MobDebugValue(v, evaluator, frameIndex, LuaExpr.child("", expr).trimStart('.')))
        }, onError = { err ->
            callback.errorOccurred(err)
        })
    }

    private fun isIdentifier(elem: PsiElement?): Boolean {
        if (elem == null) return false
        val text = elem.text
        return text.isNotEmpty() && text[0].let { it.isLetter() || it == '_' } && text.all { it.isLetterOrDigit() || it == '_' }
    }

    private fun isDotOrColon(elem: PsiElement?): Boolean {
        val t = elem?.text ?: return false
        return t == "." || t == ":"
    }

    private fun nearestFunctionStart(document: Document, offset: Int): Int? {
        val text = document.charsSequence
        val pattern = Regex("\\bfunction\\b")
        var last: Int? = null
        val until = if (offset <= text.length) offset else text.length
        pattern.findAll(text, 0).forEach { m ->
            if (m.range.first < until) last = m.range.first else return@forEach
        }
        return last
    }

    private fun expandLeftToIdentifier(base: PsiElement): PsiElement? {
        var cur: PsiElement? = base
        // If on dot/colon, move left to the previous element
        if (isDotOrColon(cur)) cur = cur?.prevSibling ?: cur?.parent?.prevSibling
        // Walk left through whitespaces and take the leftmost identifier in a chain
        while (cur != null && cur.text.isBlank()) cur = cur.prevSibling
        if (!isIdentifier(cur)) return null
        var left = cur
        // climb over preceding chain segments: <id> ('.'|':') <id>
        while (true) {
            val dot = left?.prevSibling
            if (!isDotOrColon(dot)) break
            var id = dot?.prevSibling
            while (id != null && id.text.isBlank()) id = id.prevSibling
            if (!isIdentifier(id)) break
            left = id
        }
        return left
    }

    private fun expandRightChain(base: PsiElement): PsiElement? {
        // Move to identifier if currently on dot/colon
        var cur: PsiElement? = base
        if (isDotOrColon(cur)) cur = cur?.nextSibling
        // Find the first identifier to the right (skip whitespace)
        while (cur != null && cur.text.isBlank()) cur = cur.nextSibling
        if (!isIdentifier(cur)) return null
        var right = cur
        // Consume repeated (dot/colon + identifier)
        while (true) {
            var dot: PsiElement? = right?.nextSibling
            while (dot != null && dot.text.isBlank()) dot = dot.nextSibling
            if (!isDotOrColon(dot)) break
            var id: PsiElement? = dot?.nextSibling
            while (id != null && id.text.isBlank()) id = id.nextSibling
            if (!isIdentifier(id)) break
            right = id
        }
        return right
    }
}
