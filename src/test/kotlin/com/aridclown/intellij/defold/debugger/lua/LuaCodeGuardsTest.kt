package com.aridclown.intellij.defold.debugger.lua

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LuaCodeGuardsTest {

    @Test
    fun `should keep quoted string within limit`() {
        val input = "local s = \"abc\""
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 10)
        assertThat(out).isEqualTo(input)
    }

    @Test
    fun `should trim quoted string over limit and append suffix`() {
        val input = "\"abcdefghij\""
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 5)
        assertThat(out).isEqualTo("\"abcde...(trimmed)\"")
    }

    @Test
    fun `should preserve escapes and count as single content units`() {
        val input = "\"a\\nb\\tc\\\"d\"" // content: a, \n, b, \t, c, \", d
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 3)
        // first 3 content units: a, \n, b
        assertThat(out).isEqualTo("\"a\\nb...(trimmed)\"")
    }

    @Test
    fun `should not split numeric escapes in quoted strings`() {
        val input = "\"a\\123b\""
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 2)
        // content units: 'a', '\\123' => trim after two units
        assertThat(out).isEqualTo("\"a\\123...(trimmed)\"")
    }

    @Test
    fun `should not split hex escapes in quoted strings`() {
        val input = "\"a\\xAFb\""
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 2)
        assertThat(out).isEqualTo("\"a\\xAF...(trimmed)\"")
    }

    @Test
    fun `should handle trailing backslash before quote`() {
        val input = "\"abc\\\"" // unterminated escape
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 2)
        // keep two content units 'a','b' then suffix and close
        assertThat(out).isEqualTo("\"ab...(trimmed)\"")
    }

    @Test
    fun `should keep long-bracket string within limit`() {
        val input = "[[hello]]"
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 10)
        assertThat(out).isEqualTo(input)
    }

    @Test
    fun `should trim long-bracket string over limit`() {
        val input = "[[abcdef]]"
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 3)
        assertThat(out).isEqualTo("[[abc...(trimmed)]]")
    }

    @Test
    fun `should trim long-bracket with equals over limit`() {
        val input = "[=[abcdef]=]"
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 4)
        assertThat(out).isEqualTo("[=[abcd...(trimmed)]=]")
    }

    @Test
    fun `should apply total budget across multiple strings`() {
        val input = "\"aaaaa\" .. \"bbbbb\""
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 5, totalBudget = 6)
        // first token (len=5) fully allowed, remaining=1; second token trimmed to 1
        assertThat(out).isEqualTo("\"aaaaa\" .. \"b...(trimmed)\"")
    }

    @Test
    fun `should leave non-string code unchanged`() {
        val input = "local x = 123 + 456"
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 3)
        assertThat(out).isEqualTo(input)
    }

    @Test
    fun `should trim and close unterminated quoted string`() {
        val input = "\"abcdef" // no closing quote
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 2)
        assertThat(out).isEqualTo("\"ab...(trimmed)\"")
    }

    @Test
    fun `should apply total budget across quoted and long-bracket strings`() {
        val input = "\"aaaaa\" .. [[bbbbb]]"
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 5, totalBudget = 6)
        assertThat(out).isEqualTo("\"aaaaa\" .. [[b...(trimmed)]]")
    }

    @Test
    fun `should trim long-bracket with multiple equals`() {
        val input = "[==[abcdef]==]"
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 3)
        assertThat(out).isEqualTo("[==[abc...(trimmed)]==]")
    }

    @Test
    fun `should leave unterminated long-bracket unchanged`() {
        val input = "[[abc"
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 2)
        assertThat(out).isEqualTo(input)
    }

    @Test
    fun `should preserve unicode content when trimming`() {
        val input = "\"😀😀😀😀\""
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 2)
        assertThat(out).isEqualTo("\"😀😀...(trimmed)\"")
    }

    @Test
    fun `should handle whitespace escape z correctly`() {
        val input = "\"a\\z   \tb\"" // \z should skip whitespace
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 2)
        assertThat(out).isEqualTo("\"a\\z   \t...(trimmed)\"")
    }

    @Test
    fun `should handle single quotes same as double quotes`() {
        val input = "'abcdef'"
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 3)
        assertThat(out).isEqualTo("'abc...(trimmed)'")
    }

    @Test
    fun `should handle mixed quote types in same code`() {
        val input = "\"abc\" + 'def'"
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 2, totalBudget = 4)
        // First string: "abc" (len=3) -> limited to 2 by limit, remaining budget = 4-2=2
        // Second string: 'def' (len=3) -> limited to 1 by remaining budget (since we only have 1 left after budget calculation)
        assertThat(out).isEqualTo("\"ab...(trimmed)\" + 'd...(trimmed)'")
    }

    @Test
    fun `should handle incomplete hex escape sequences`() {
        val input = "\"a\\xGbcd\"" // invalid hex: 'a', '\xG', 'b', 'c', 'd' = 5 content units
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 2)
        assertThat(out).isEqualTo("\"a\\xG...(trimmed)\"")
    }

    @Test
    fun `should handle incomplete numeric escape sequences`() {
        val input = "\"a\\9999\"" // more than 3 digits, should only take first 3
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 2)
        assertThat(out).isEqualTo("\"a\\999...(trimmed)\"")
    }

    @Test
    fun `should handle zero-length strings`() {
        val input = "\"\""
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 5)
        assertThat(out).isEqualTo("\"\"")
    }

    @Test
    fun `should handle zero-length long bracket strings`() {
        val input = "[[]]"
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 5)
        assertThat(out).isEqualTo("[[]]")
    }

    @Test
    fun `should handle malformed long bracket opening`() {
        val input = "[=abc" // missing second [
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 2)
        assertThat(out).isEqualTo(input) // should remain unchanged
    }

    @Test
    fun `should handle nested bracket-like sequences in long strings`() {
        val input = "[[a[=b=]c]]"
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 3)
        assertThat(out).isEqualTo("[[a[=...(trimmed)]]")
    }

    @Test
    fun `should handle escape at end of quoted string`() {
        val input = "\"abc\\\\\""
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 2)
        assertThat(out).isEqualTo("\"ab...(trimmed)\"")
    }

    @Test
    fun `should handle high unicode surrogates correctly`() {
        val input = "\"a👨b\"" // simpler emoji test - content: 'a', '👨', 'b'
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 2)
        // Should handle surrogate pairs properly and take first 2 content units
        assertThat(out).isEqualTo("\"a👨...(trimmed)\"")
    }

    @Test
    fun `should handle totalBudget of zero`() {
        val input = "\"abc\" + \"def\""
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 5, totalBudget = 0)
        assertThat(out).isEqualTo("\"...(trimmed)\" + \"...(trimmed)\"")
    }

    @Test
    fun `should handle very large equal sign counts in long brackets`() {
        val equalSigns = "=".repeat(10)
        val input = "[$equalSigns[content]$equalSigns]"
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 3)
        assertThat(out).isEqualTo("[$equalSigns[con...(trimmed)]$equalSigns]")
    }

    @Test
    fun `should handle long bracket with unmatched closing sequence`() {
        val input = "[=[content]==]" // wrong number of equals
        val out = LuaCodeGuards.limitStringLiterals(input, limit = 3)
        assertThat(out).isEqualTo(input) // should remain unchanged as it's not properly terminated
    }
}
