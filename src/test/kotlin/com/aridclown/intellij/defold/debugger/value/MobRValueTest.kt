package com.aridclown.intellij.defold.debugger.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.luaj.vm2.LuaBoolean
import org.luaj.vm2.LuaString
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue

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
        assertThat(rv).isInstanceOfSatisfying(MobRValue.Vector::class.java) { vector ->
            assertThat(vector.components).containsExactly(1.0, 2.0, 3.0)
            assertThat(vector.typeLabel).isEqualTo("vector3")
            assertThat(vector.preview).isEqualTo("(1.0, 2.0, 3.0)")
        }
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
        assertThat(rv).isInstanceOfSatisfying(MobRValue.Vector::class.java) { vector ->
            assertThat(vector.components).containsExactly(1.0, 2.0, 3.0, 4.0)
            assertThat(vector.typeLabel).isEqualTo("vector4")
            assertThat(vector.preview).isEqualTo("(1.0, 2.0, 3.0, 4.0)")
        }
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
        assertThat(rv).isInstanceOfSatisfying(MobRValue.Quat::class.java) { quat ->
            assertThat(quat.components).containsExactly(1.0, 0.0, 0.0, 0.0)
            assertThat(quat.typeLabel).isEqualTo("quat")
            assertThat(quat.preview).isEqualTo("(1.0, 0.0, 0.0, 0.0)")
        }
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
        assertThat(rv).isInstanceOfSatisfying(MobRValue.Matrix::class.java) { matrix ->
            val expected = listOf(
                listOf(1.0, 2.0, 3.0, 4.0),
                listOf(5.0, 6.0, 7.0, 8.0),
                listOf(9.0, 10.0, 11.0, 12.0),
                listOf(13.0, 14.0, 15.0, 16.0)
            )
            assertThat(matrix.rows).isEqualTo(expected)
            assertThat(matrix.typeLabel).isEqualTo("matrix4")
        }
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
        assertThat(rv).isInstanceOfSatisfying(MobRValue.Hash::class.java) { hash ->
            assertThat(hash.value).isEqualTo("example")
            assertThat(hash.typeLabel).isEqualTo("hash")
            assertThat(hash.preview).isEqualTo("example")
        }
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
        assertThat(rv).isInstanceOfSatisfying(MobRValue.Url::class.java) { url ->
            assertThat(url.socket).isEqualTo("main")
            assertThat(url.path).isEqualTo("/path")
            assertThat(url.fragment).isEqualTo("frag")
            assertThat(url.preview).isEqualTo("main:/path#frag")
        }
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
        assertThat(rv).isInstanceOfSatisfying(MobRValue.Message::class.java) { message ->
            assertThat(message.id).isEqualTo("hello")
            assertThat(message.typeLabel).isEqualTo("message")
            assertThat(message.preview).isEqualTo("hello")
        }
    }

    @Test
    fun `nil entry maps to Nil`() {
        val rv = MobRValue.fromRawLuaValue(LuaValue.NIL)
        assertThat(rv).isSameAs(MobRValue.Nil)
    }

    @Test
    fun `boolean entry maps to Bool`() {
        val rv = MobRValue.fromRawLuaValue(LuaBoolean.TRUE)
        assertThat(rv).isInstanceOfSatisfying(MobRValue.Bool::class.java) { bool ->
            assertThat(bool.content).isTrue()
            assertThat(bool.typeLabel).isEqualTo("boolean")
        }
    }

    @Test
    fun `script instance userdata is parsed`() {
        val raw = LuaUserdata(Any())
        val desc = LuaString.valueOf("Script: hero#main")
        val table = LuaTable().apply {
            set(1, raw)
            set(2, desc)
        }

        val rv = MobRValue.fromLuaEntry(table)

        assertThat(rv).isInstanceOfSatisfying(MobRValue.ScriptInstance::class.java) { instance ->
            assertThat(instance.kind).isEqualTo(MobRValue.ScriptInstance.Kind.GAME_OBJECT)
            assertThat(instance.identity).isEqualTo("hero#main")
            assertThat(instance.typeLabel).isEqualTo("script")
            assertThat(instance.preview).isEqualTo("hero#main")
        }
    }

    @Test
    fun `gui script instance userdata is parsed`() {
        val raw = LuaUserdata(Any())
        val desc = LuaString.valueOf("GuiScript: /gui/test.gui_script")
        val table = LuaTable().apply {
            set(1, raw)
            set(2, desc)
        }

        val rv = MobRValue.fromLuaEntry(table)

        assertThat(rv).isInstanceOfSatisfying(MobRValue.ScriptInstance::class.java) { instance ->
            assertThat(instance.kind).isEqualTo(MobRValue.ScriptInstance.Kind.GUI)
            assertThat(instance.identity).isEqualTo("/gui/test.gui_script")
            assertThat(instance.typeLabel).isEqualTo("gui script")
        }
    }

    @Test
    fun `render script instance userdata is parsed`() {
        val raw = LuaUserdata(Any())
        val desc = LuaString.valueOf("RenderScript: render#main")
        val table = LuaTable().apply {
            set(1, raw)
            set(2, desc)
        }

        val rv = MobRValue.fromLuaEntry(table)

        assertThat(rv).isInstanceOfSatisfying(MobRValue.ScriptInstance::class.java) { instance ->
            assertThat(instance.kind).isEqualTo(MobRValue.ScriptInstance.Kind.RENDER)
            assertThat(instance.identity).isEqualTo("render#main")
            assertThat(instance.typeLabel).isEqualTo("render script")
        }
    }
}
