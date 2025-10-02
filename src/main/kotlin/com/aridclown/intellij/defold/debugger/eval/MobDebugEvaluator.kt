package com.aridclown.intellij.defold.debugger.eval

import com.aridclown.intellij.defold.DefoldConstants.EXEC_MAXLEVEL
import com.aridclown.intellij.defold.debugger.MobDebugProtocol
import com.aridclown.intellij.defold.debugger.lua.LuaSandbox
import com.aridclown.intellij.defold.debugger.lua.isVarargs
import org.luaj.vm2.LuaValue

/**
 * EmmyLua-style evaluator for MobDebug EXEC results.
 * - Sends: EXEC "return <expr>" -- { stack = <frame> }
 * - Reconstructs the returned Lua value using LuaJ on the IDE side.
 */
class MobDebugEvaluator(private val protocol: MobDebugProtocol) {

    fun evaluateExpr(
        frameIndex: Int,
        expr: String,
        onSuccess: (LuaValue) -> Unit,
        onError: (String) -> Unit
    ) {
        protocol.exec("return $expr", frame = frameIndex, options = "maxlevel = $EXEC_MAXLEVEL", onResult = { body ->
            try {
                onSuccess(
                    reconstructFromBody(body, expr.isVarargs())
                )
            } catch (t: Throwable) {
                onError("Failed to evaluate: ${t.message ?: t.toString()}")
            }
        }, onError = { err ->
            onError(err.details ?: err.message)
        })
    }

    fun executeStatement(
        frameIndex: Int,
        statement: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        protocol.exec(statement, frame = frameIndex, options = "maxlevel = $EXEC_MAXLEVEL", onResult = { _ ->
            // For statements, we don't need to reconstruct a return value
            onSuccess()
        }, onError = { err ->
            onError(err.details ?: err.message)
        })
    }

    /**
     * MobDebug returns a chunk that, when executed, yields a table of serialized results.
     * We take the first result, then reconstruct the true value.
     */
    private fun reconstructFromBody(body: String, isVarargs: Boolean): LuaValue {
        val globals = LuaSandbox.sharedGlobals()
        val tableOfSerialized = globals.load(body, "exec_result").call()

        /**
         * Special handling for varargs: reconstruct all values, not just the first.
         * When evaluating "...", MobDebug returns all values in the table.
         */
        fun reconstructVarargs(): LuaValue {
            val table = tableOfSerialized.checktable()
            val length = table.length()

            // Create a new table with all reconstructed values
            val result = LuaValue.tableOf()
            for (i in 1..length) {
                val serialized = table.get(i)
                val value = globals.load("local _=${serialized.tojstring()} return _", "recon").call()
                result.set(i, value)
            }
            return result
        }

        return when {
            isVarargs && tableOfSerialized.istable() -> reconstructVarargs()
            else -> {
                val serialized = tableOfSerialized.get(1).tojstring()
                globals.load("local _=$serialized return _", "recon").call()
            }
        }
    }
}
