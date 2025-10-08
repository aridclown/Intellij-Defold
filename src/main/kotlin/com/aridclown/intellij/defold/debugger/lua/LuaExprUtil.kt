package com.aridclown.intellij.defold.debugger.lua

import com.aridclown.intellij.defold.DefoldConstants.ELLIPSIS_VAR
import org.luaj.vm2.LuaValue

private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val numeric = Regex("-?\\d+(?:\\.\\d+)?")
private val varargNameRegex = Regex("""\(\*vararg (\d+)\)""")

fun child(parentExpr: String, keyName: String): String = when {
    identifier.matches(keyName) -> "$parentExpr.$keyName"
    numeric.matches(keyName) -> "$parentExpr[$keyName]"
    else -> "$parentExpr[${quote(keyName)}]"
}

private fun quote(s: String): String = buildString {
    append('"')
    for (ch in s) when (ch) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\n' -> append("\\n")
        '\t' -> append("\\t")
        '\r' -> append("\\r")
        else -> append(ch)
    }
    append('"')
}

fun String.isVarargs() = this.trim() == ELLIPSIS_VAR
fun String.isVarargName(): Boolean = varargNameRegex.matches(this)
fun varargExpression(name: String): String =
    varargNameRegex.matchEntire(name)?.groupValues?.getOrNull(1)?.let { index ->
        "select($index, ...)"
    } ?: name

fun LuaValue.toStringSafely(): String = try {
    tojstring()
} catch (_: Throwable) {
    toString()
}
