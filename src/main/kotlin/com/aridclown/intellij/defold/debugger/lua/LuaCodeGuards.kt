package com.aridclown.intellij.defold.debugger.lua

/**
 * Lightweight guard to limit individual Lua string tokens' size inside code dumps
 * before feeding them to LuaJ. This mirrors EmmyLua's approach of trimming long
 * string literals in the serialized code to prevent huge payloads.
 */
object LuaCodeGuards {
    fun limitStringLiterals(code: String, limit: Int): String {
        val sb = StringBuilder(code.length)
        var i = 0
        while (i < code.length) {
            val ch = code[i]
            if (ch == '"' || ch == '\'') {
                val start = i
                i++
                var escaped = false
                var count = 0
                while (i < code.length) {
                    val c = code[i]
                    if (!escaped && c == ch) {
                        i++
                        break
                    }
                    if (!escaped && c == '\\') {
                        escaped = true
                    } else {
                        escaped = false
                        count++
                    }
                    i++
                }
                val token = code.substring(start, i)
                if (count > limit) {
                    // keep prefix of content up to limit, append an indicator
                    val trimmed = buildString {
                        append(ch)
                        var taken = 0
                        var j = start + 1
                        var esc = false
                        while (j < i - 1 && taken < limit) {
                            val cc = code[j]
                            append(cc)
                            esc = !esc && cc == '\\'
                            if (!esc) taken++
                            j++
                        }
                        append("...(trimmed)")
                        append(ch)
                    }
                    sb.append(trimmed)
                } else {
                    sb.append(token)
                }
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }
}