package com.aridclown.intellij.defold.debugger.eval

import com.aridclown.intellij.defold.DefoldConstants.EXEC_MAXLEVEL
import com.aridclown.intellij.defold.DefoldConstants.STACK_STRING_TOKEN_LIMIT
import com.aridclown.intellij.defold.debugger.MobDebugProtocol
import com.aridclown.intellij.defold.debugger.lua.LuaSandbox
import com.aridclown.intellij.defold.debugger.lua.LuaCodeGuards
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
                val value = reconstructFromBody(body)
                onSuccess(value)
            } catch (t: Throwable) {
                onError("Failed to evaluate: ${t.message ?: t.toString()}")
            }
        }, onError = { err ->
            onError(err.details ?: err.message)
        })
    }

    /**
     * MobDebug returns a chunk that, when executed, yields a table of serialized results.
     * We take the first result, then reconstruct the true value.
     */
    private fun reconstructFromBody(body: String): LuaValue {
        val globals = LuaSandbox.sharedGlobals()
        val guarded = LuaCodeGuards.limitStringLiterals(body, STACK_STRING_TOKEN_LIMIT)
        val tableOfSerialized = globals.load(guarded, "exec_result").call()
        val serialized = tableOfSerialized.get(1).tojstring()
        return globals.load("local _=$serialized return _", "recon").call()
    }
}
