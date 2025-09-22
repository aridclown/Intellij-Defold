package com.aridclown.intellij.defold.debugger

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MobDebugStackParserTest {

    @Test
    fun `preserves variable order from stack metadata`() {
        val dump = """
            return {
              current = {
                id = "main",
                status = "running",
                frameBase = 3,
                stack = {
                  {
                    { "myfunc", "@/script.lua", 1, 2 },
                    {
                      __order = { "arg1", "localValue", "(vararg 1)" },
                      arg1 = { "first", "first" },
                      localValue = { 123, "123" },
                      ["(vararg 1)"] = { "extra", "extra" },
                    },
                    {
                      __order = { "upvalue" },
                      upvalue = { true, "true" },
                    },
                  },
                },
              },
              coroutines = {},
            }
        """.trimIndent()

        val stack = MobDebugStackParser.parseStackDump(dump)
        val names = stack.current.frames.first().variables.map { it.name }

        assertThat(names).containsExactly("arg1", "localValue", "(vararg 1)", "upvalue")
    }

    @Test
    fun `falls back gracefully when order metadata is absent`() {
        val dump = """
            return {
              current = {
                id = "main",
                status = "running",
                frameBase = 3,
                stack = {
                  {
                    { "other", "@/other.lua", 1, 4 },
                    {
                      alpha = { "value", "value" },
                      beta = { "second", "second" },
                    },
                    {},
                  },
                },
              },
              coroutines = {},
            }
        """.trimIndent()

        val stack = MobDebugStackParser.parseStackDump(dump)
        val names = stack.current.frames.first().variables.map { it.name }

        assertThat(names).containsExactlyInAnyOrder("alpha", "beta")
    }
}
