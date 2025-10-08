package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.debugger.lua.LuaSandbox
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class TableChildrenPagerTest {

    @Test
    fun `numbers come before strings and sort by tojstring`() {
        val t = LuaTable()
        // set keys in mixed order
        t.set(2, LuaValue.valueOf("v2"))
        t.set("a", LuaValue.valueOf("va"))
        t.set(10, LuaValue.valueOf("v10"))
        t.set("b", LuaValue.valueOf("vb"))
        t.set(1, LuaValue.valueOf("v1"))

        val sorted = TableChildrenPager.sortedKeys(t)
        val names = sorted.map { it.tojstring() }

        // numeric keys first by string value: 1,10,2 then strings: a,b
        assertThat(names).containsExactly("1", "10", "2", "a", "b")
    }

    @Test
    fun `build slice creates child entries with correct expr`() {
        val base = "root"
        val t = LuaTable()
        // keys: identifier, numeric, quoted string
        t.set("plain", LuaValue.valueOf(42))
        t.set(7, LuaValue.valueOf("seven"))
        t.set("with space", LuaValue.valueOf(true))

        val keys = TableChildrenPager.sortedKeys(t)
        val slice = TableChildrenPager.buildSlice(base, t, keys, 0, keys.size)

        // Map by name -> expr for easy assertions
        val exprByName = slice.associate { it.name to it.expr }

        assertThat(exprByName)
            .containsEntry("plain", "root.plain")
            .containsEntry("7", "root[7]")
            .containsEntry("with space", "root[\"with space\"]")
    }
}
