package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.debugger.MobDebugProcess
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.value.MobRValue.*
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.frame.XValueModifier

class MobDebugValueModifier(
    private val evaluator: MobDebugEvaluator,
    private val frameIndex: Int,
    private val variable: MobVariable,
    private val debugProcess: MobDebugProcess
) : XValueModifier() {

    override fun setValue(expression: XExpression, callback: XModificationCallback) {
        val newValueExpr = expression.expression.trim()
        if (newValueExpr.isEmpty()) {
            return
        }

        // Use the evaluator to set the variable value
        val assignmentStatement = "${variable.name} = $newValueExpr"

        evaluator.executeStatement(
            frameIndex = frameIndex,
            statement = assignmentStatement,
            onSuccess = {
                debugProcess.refreshCurrentStackFrame { callback.valueModified() }
            },
            onError = { error ->
                callback.errorOccurred("Failed to set value: $error")
            }
        )
    }

    override fun getInitialValueEditorText(): String? = when (val value = variable.value) {
        is Str -> "\"${value.content}\""
        is Num -> value.content
        is Bool -> if (value.content) "true" else "false"
        is Nil -> "nil"
        else -> null  // For complex types, let the user type from scratch
    }
}