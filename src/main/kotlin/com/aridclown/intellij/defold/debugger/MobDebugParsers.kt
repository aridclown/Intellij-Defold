package com.aridclown.intellij.defold.debugger

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform

data class FrameInfo(
    val source: String?,
    val line: Int?,
    val name: String?,
    val variables: List<MobDebugVariable> = emptyList()
)

object MobDebugParsers {
    private const val IDX_INFO = 1
    private const val IDX_LOCALS = 2
    private const val IDX_UPVALUES = 3
    private const val INFO_FUNCNAME = 1
    private const val INFO_SOURCE = 2
    private const val INFO_CURRENTLINE = 4

    // Parse Lua code dump into frames with variables (locals + upvalues)
    fun parseStackDump(dump: String): List<FrameInfo> = try {
        val globals = JsePlatform.standardGlobals()
        val value = globals.load(dump, "mobdebug_stack_dump").call()
        val frames = mutableListOf<FrameInfo>()
        var i = 1
        while (true) {
            val frameTable = value.get(i)
            if (frameTable.isnil()) break
            frames.add(parseFrame(frameTable))
            i++
        }
        frames
    } catch (_: Throwable) {
        emptyList()
    }

    private fun parseFrame(frameTable: LuaValue): FrameInfo {
        val info = frameTable.get(IDX_INFO)
        val name = info.get(INFO_FUNCNAME).takeUnless { it.isnil() }?.tojstring() ?: "main"
        val source = info.get(INFO_SOURCE).takeUnless { it.isnil() }?.tojstring()
        val line = info.get(INFO_CURRENTLINE).takeUnless { it.isnil() }?.toint()

        val variables = buildList {
            addAll(readVars(frameTable.get(IDX_LOCALS)))
            addAll(readVars(frameTable.get(IDX_UPVALUES)))
        }
        return FrameInfo(source, line, name, variables)
    }

    private fun readVars(value: LuaValue): List<MobDebugVariable> {
        if (!value.istable()) return emptyList()
        val table: LuaTable = value.checktable()
        val vars = mutableListOf<MobDebugVariable>()
        for (key in table.keys()) {
            val entry = table.get(key)
            val name = safeToString(key)
            val preview = if (entry.istable()) safeToString(entry.get(2)) else safeToString(entry)
            vars.add(MobDebugVariable(name, preview))
        }
        return vars
    }

    private fun safeToString(v: LuaValue): String = try {
        v.tojstring()
    } catch (_: Throwable) {
        v.toString()
    }
}
