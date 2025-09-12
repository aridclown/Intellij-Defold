package com.aridclown.intellij.defold.debugger.lua

/**
 * Limits individual Lua string tokens inside code (quoted and long bracket strings)
 * before feeding them to LuaJ. This keeps parseable code while trimming payloads.
 */
object LuaCodeGuards {
    private const val TRIM_SUFFIX = "...(trimmed)"

    fun limitStringLiterals(code: String, limit: Int, totalBudget: Int? = null): String {
        val out = StringBuilder(code.length)
        var remaining = totalBudget ?: Int.MAX_VALUE
        var i = 0

        while (i < code.length) {
            when (val ch = code[i]) {
                '\'', '"' -> {
                    val result = processQuotedString(code, i, ch, limit, remaining)
                    out.append(result.output)
                    remaining = maxOf(0, remaining - result.contentLen)
                    i = result.nextIndex
                }

                '[' -> {
                    val result = processLongBracketString(code, i, limit, remaining)
                    if (result.isStringToken) {
                        out.append(result.output)
                        remaining = maxOf(0, remaining - result.contentLen)
                        i = result.nextIndex
                    } else {
                        out.append(ch)
                        i++
                    }
                }

                else -> {
                    out.append(ch)
                    i++
                }
            }
        }
        return out.toString()
    }

    private data class ProcessResult(
        val output: String,
        val nextIndex: Int,
        val contentLen: Int,
        val isStringToken: Boolean = true
    )

    private fun processQuotedString(s: String, start: Int, quote: Char, limit: Int, remaining: Int): ProcessResult {
        val (end, contentLen) = scanQuotedString(s, start, quote)
        val allowedLen = minOf(limit, remaining, contentLen)

        return when {
            contentLen <= allowedLen -> ProcessResult(s.substring(start, end), end, contentLen)
            else -> ProcessResult(
                output = buildTrimmedQuotedString(s, start, end, quote, allowedLen),
                nextIndex = end,
                contentLen
            )
        }
    }

    private fun processLongBracketString(s: String, start: Int, limit: Int, remaining: Int): ProcessResult {
        val opening = parseLongBracketOpening(s, start)
        if (!opening.isValid) return ProcessResult("", start, 0, false)

        val closing = findLongBracketClosing(s, opening.contentStart, opening.equalSigns)
        if (!closing.found) return ProcessResult("", start, 0, false)

        val contentLen = closing.contentEnd - opening.contentStart
        val allowedLen = minOf(limit, remaining, contentLen)

        return when {
            contentLen <= allowedLen -> ProcessResult(
                s.substring(start, closing.closeEnd),
                closing.closeEnd,
                contentLen
            )

            else -> ProcessResult(
                output = buildTrimmedLongBracket(s, opening, allowedLen),
                nextIndex = closing.closeEnd,
                contentLen
            )
        }
    }

    private fun scanQuotedString(s: String, start: Int, quote: Char): Pair<Int, Int> {
        var i = start + 1
        var contentLen = 0

        while (i < s.length && s[i] != quote) {
            i = advanceCharacter(s, i, s.length)
            contentLen++
        }

        return (if (i < s.length) i + 1 else i) to contentLen
    }

    private fun buildTrimmedQuotedString(s: String, start: Int, end: Int, quote: Char, take: Int): String {
        val result = StringBuilder()
        result.append(quote)

        var pos = start + 1
        var taken = 0

        while (pos < end - 1 && taken < take) {
            val nextPos = advanceCharacter(s, pos, end - 1)
            result.append(s, pos, nextPos)
            pos = nextPos
            taken++
        }

        result.append(TRIM_SUFFIX)
        result.append(quote)
        return result.toString()
    }

    private fun advanceCharacter(s: String, pos: Int, boundary: Int): Int {
        val c = s[pos]
        return when {
            c == '\\' -> skipEscape(s, pos, boundary)
            c.isHighSurrogate() && pos + 1 < boundary && s[pos + 1].isLowSurrogate() -> pos + 2
            else -> pos + 1
        }
    }

    private fun skipEscape(s: String, pos: Int, boundary: Int): Int {
        if (pos + 1 >= boundary) return minOf(pos + 1, boundary)

        return when (s[pos + 1]) {
            'x', 'X' -> skipHexEscape(s, pos + 2, boundary)
            in '0'..'9' -> skipOctalEscape(s, pos + 1, boundary)
            'z' -> skipWhitespaceEscape(s, pos + 2, boundary)
            else -> minOf(pos + 2, boundary)
        }
    }

    private fun skipHexEscape(s: String, start: Int, boundary: Int): Int {
        var i = start
        var count = 0
        while (i < boundary && count < 2 && s[i].isHexDigit()) {
            i++
            count++
        }

        // If no hex digits were found, consume at least one character if available
        if (count == 0 && i < boundary) {
            i++
        }
        return i
    }

    private fun skipOctalEscape(s: String, start: Int, boundary: Int): Int {
        var i = start
        var count = 0
        while (i < boundary && count < 3 && s[i].isDigit()) {
            i++
            count++
        }
        return i
    }

    private fun skipWhitespaceEscape(s: String, start: Int, boundary: Int): Int {
        var i = start
        while (i < boundary && s[i].isWhitespace()) i++
        return i
    }

    private data class LongBracketOpening(
        val isValid: Boolean,
        val equalSigns: Int,
        val contentStart: Int
    )

    private data class LongBracketClosing(
        val found: Boolean,
        val contentEnd: Int,
        val closeEnd: Int
    )

    private fun parseLongBracketOpening(s: String, start: Int): LongBracketOpening {
        var pos = start + 1
        var equalCount = 0

        while (pos < s.length && s[pos] == '=') {
            equalCount++
            pos++
        }

        return when {
            pos < s.length && s[pos] == '[' -> LongBracketOpening(true, equalCount, pos + 1)
            else -> LongBracketOpening(false, 0, 0)
        }
    }

    private fun findLongBracketClosing(s: String, contentStart: Int, equalSigns: Int): LongBracketClosing {
        var pos = contentStart

        while (pos < s.length) {
            if (s[pos] == ']') {
                val closeEnd = checkClosingSequence(s, pos, equalSigns)
                if (closeEnd != -1) {
                    return LongBracketClosing(true, pos, closeEnd)
                }
            }
            pos++
        }

        return LongBracketClosing(false, -1, -1)
    }

    private fun checkClosingSequence(s: String, start: Int, expectedEquals: Int): Int {
        var pos = start + 1
        var equalCount = 0

        while (equalCount < expectedEquals && pos < s.length && s[pos] == '=') {
            equalCount++
            pos++
        }

        return if (equalCount == expectedEquals && pos < s.length && s[pos] == ']') {
            pos + 1
        } else {
            -1
        }
    }

    private fun buildTrimmedLongBracket(s: String, opening: LongBracketOpening, take: Int) = buildString {
        append('[')
        repeat(opening.equalSigns) { append('=') }
        append('[')
        if (take > 0) {
            append(s, opening.contentStart, opening.contentStart + take)
        }
        append(TRIM_SUFFIX)
        append(']')
        repeat(opening.equalSigns) { append('=') }
        append(']')
    }

    private fun Char.isHexDigit(): Boolean = isDigit() || this in 'a'..'f' || this in 'A'..'F'
}
