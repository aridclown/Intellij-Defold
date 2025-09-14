package com.aridclown.intellij.defold.debugger.value

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.luaj.vm2.LuaString
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata

class MobRValueTest {
    @Test
    fun `vector3 userdata is parsed`() {
        val raw = LuaUserdata(Any())
        val desc = LuaString.valueOf("vmath.vector3(1, 2, 3)")
        val table = LuaTable().apply {
            set(1, raw)
            set(2, desc)
        }
        val rv = MobRValue.fromLuaEntry(table)
        val vector = rv as? MobRValue.Vector
        assertNotNull(vector)
        assertEquals(listOf(1.0, 2.0, 3.0), vector!!.components)
        assertEquals("vector3", vector.typeLabel)
        assertEquals("(1.0, 2.0, 3.0)", vector.preview)
    }

    @Test
    fun `vector4 userdata is parsed`() {
        val raw = LuaUserdata(Any())
        val desc = LuaString.valueOf("vmath.vector4(1, 2, 3, 4)")
        val table = LuaTable().apply {
            set(1, raw)
            set(2, desc)
        }
        val rv = MobRValue.fromLuaEntry(table)
        val vector = rv as? MobRValue.Vector
        assertNotNull(vector)
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0), vector!!.components)
        assertEquals("vector4", vector.typeLabel)
        assertEquals("(1.0, 2.0, 3.0, 4.0)", vector.preview)
    }

    @Test
    fun `quat userdata is parsed`() {
        val raw = LuaUserdata(Any())
        val desc = LuaString.valueOf("vmath.quat(1, 0, 0, 0)")
        val table = LuaTable().apply {
            set(1, raw)
            set(2, desc)
        }
        val rv = MobRValue.fromLuaEntry(table)
        val quat = rv as? MobRValue.Quat
        assertNotNull(quat)
        assertEquals(listOf(1.0, 0.0, 0.0, 0.0), quat!!.components)
        assertEquals("quat", quat.typeLabel)
        assertEquals("(1.0, 0.0, 0.0, 0.0)", quat.preview)
    }

    @Test
    fun `matrix4 userdata is parsed`() {
        val raw = LuaUserdata(Any())
        val desc = LuaString.valueOf("vmath.matrix4(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16)")
        val table = LuaTable().apply {
            set(1, raw)
            set(2, desc)
        }
        val rv = MobRValue.fromLuaEntry(table)
        val matrix = rv as? MobRValue.Matrix
        assertNotNull(matrix)
        val expected = listOf(
            listOf(1.0, 2.0, 3.0, 4.0),
            listOf(5.0, 6.0, 7.0, 8.0),
            listOf(9.0, 10.0, 11.0, 12.0),
            listOf(13.0, 14.0, 15.0, 16.0)
        )
        assertEquals(expected, matrix!!.rows)
        assertEquals("matrix4", matrix.typeLabel)
    }

    @Test
    fun `hash userdata is parsed`() {
        val raw = LuaUserdata(Any())
        val desc = LuaString.valueOf("hash: [example]")
        val table = LuaTable().apply {
            set(1, raw)
            set(2, desc)
        }
        val rv = MobRValue.fromLuaEntry(table)
        val hash = rv as? MobRValue.Hash
        assertNotNull(hash)
        assertEquals("example", hash!!.value)
        assertEquals("hash", hash.typeLabel)
        assertEquals("example", hash.preview)
    }

    @Test
    fun `url userdata is parsed`() {
        val raw = LuaUserdata(Any())
        val desc = LuaString.valueOf("url: [main:/path#frag]")
        val table = LuaTable().apply {
            set(1, raw)
            set(2, desc)
        }
        val rv = MobRValue.fromLuaEntry(table)
        val url = rv as? MobRValue.Url
        assertNotNull(url)
        assertEquals("main", url!!.socket)
        assertEquals("/path", url.path)
        assertEquals("frag", url.fragment)
        assertEquals("main:/path#frag", url.preview)
    }

    @Test
    fun `message userdata is parsed`() {
        val raw = LuaUserdata(Any())
        val desc = LuaString.valueOf("message: [hello]")
        val table = LuaTable().apply {
            set(1, raw)
            set(2, desc)
        }
        val rv = MobRValue.fromLuaEntry(table)
        val msg = rv as? MobRValue.Message
        assertNotNull(msg)
        assertEquals("hello", msg!!.id)
        assertEquals("message", msg.typeLabel)
        assertEquals("hello", msg.preview)
    }
}
