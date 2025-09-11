package com.aridclown.intellij.defold.debugger.lua

/**
 * Helpers to build safe Lua expressions for nested field/index access.
 */
object LuaExpr {
    private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val numeric = Regex("-?\\d+(?:\\.\\d+)?")

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
}

