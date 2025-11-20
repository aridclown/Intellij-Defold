package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.debugger.MobDebugProcess
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.value.MobRValue.*
import com.aridclown.intellij.defold.debugger.value.MobVariable.Kind
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.frame.XValueModifier

/**
 * Modifier that allows editing of variable values in the MobDebug debugger.
 *
 * Supports editing of:
 * - Simple types (strings, numbers, booleans, nil, hashes)
 * - Children of complex types (e.g., vector.x, table[key], url.socket)
 *
 * Examples of editable expressions:
 * - `myVector.x = 2.5` (vector component)
 * - `myTable["key"] = "value"` (table element)
 * - `myUrl.socket = "other"` (URL component)
 * - `myHash = hash("newvalue")` (hash value)
 */
class MobDebugValueModifier(
    private val evaluator: MobDebugEvaluator,
    private val frameIndex: Int,
    private val variable: MobVariable,
    private val debugProcess: MobDebugProcess
) : XValueModifier() {

    private val identifierRegex = Regex("[A-Za-z_][A-Za-z0-9_]*")

    override fun setValue(expression: XExpression, callback: XModificationCallback) {
        val newValueExpr = expression.expression.trim()
        if (newValueExpr.isEmpty()) {
            return
        }

        // Use the variable's expression which includes the full path (e.g., "myVector.x", "myTable[1]")
        val targetExpr = variable.expression.takeIf { it.isNotBlank() } ?: variable.name
        val assignmentStatement = buildAssignment(targetExpr, newValueExpr)

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

    private fun buildAssignment(targetExpr: String, newValueExpr: String): String {
        val isIdentifier = identifierRegex.matches(targetExpr)
        val isLocalKind = when (variable.kind) {
            Kind.LOCAL, Kind.PARAMETER, Kind.UPVALUE -> true
        }

        if (isIdentifier && isLocalKind) {
            return "local __env = getfenv(1); rawset(__env, \"$targetExpr\", $newValueExpr)"
        }

        return "$targetExpr = $newValueExpr"
    }

    override fun getInitialValueEditorText(): String? = when (val value = variable.value) {
        is Str -> "\"${value.content}\""
        is Num -> value.content
        is Bool -> if (value.content) "true" else "false"
        is Nil -> "nil"
        is Hash -> "hash(\"${value.value}\")"
        else -> null  // For complex types, let the user type from scratch
    }
}
