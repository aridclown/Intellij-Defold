package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldConstants.STACK_STRING_TOKEN_LIMIT
import com.aridclown.intellij.defold.debugger.lua.LuaCodeGuards
import com.aridclown.intellij.defold.debugger.lua.LuaSandbox
import com.aridclown.intellij.defold.debugger.lua.toStringSafely
import com.aridclown.intellij.defold.debugger.lua.varargExpression
import com.aridclown.intellij.defold.debugger.value.MobRValue
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.aridclown.intellij.defold.debugger.value.MobVariable.Kind
import com.aridclown.intellij.defold.debugger.value.MobVariable.Kind.*
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

private val ORDER_KEY: LuaValue = LuaValue.valueOf("__order")
private val PARAM_COUNT_KEY: LuaValue = LuaValue.valueOf("__params")

data class FrameInfo(
    val source: String?,
    val line: Int?,
    val name: String?,
    val variables: List<MobVariable> = emptyList()
)

data class CoroutineStackInfo(
    val id: String,
    val status: String,
    val frames: List<FrameInfo>,
    val frameBase: Int,
    val isCurrent: Boolean
)

data class StackDump(
    val current: CoroutineStackInfo,
    val others: List<CoroutineStackInfo>
)

object MobDebugStackParser {
    private const val DEFAULT_FRAME_BASE = 3
    private const val IDX_INFO = 1
    private const val IDX_LOCALS = 2
    private const val IDX_UPVALUES = 3
    private const val INFO_FUNCNAME = 1
    private const val INFO_SOURCE = 2
    private const val INFO_CURRENTLINE = 4

    fun parseStackDump(dump: String): StackDump = try {
        val globals = LuaSandbox.sharedGlobals()
        val guarded = LuaCodeGuards.limitStringLiterals(dump, STACK_STRING_TOKEN_LIMIT)
        val value = globals.load(guarded, "mobdebug_stack_dump").call()
        when {
            value.istable() && !value.get("current").isnil() -> parseCoroutineAwareDump(value.checktable())
            else -> parseLegacyDump(value)
        }
    } catch (_: Throwable) {
        StackDump(
            current = CoroutineStackInfo("main", "running", emptyList(), DEFAULT_FRAME_BASE, true),
            others = emptyList()
        )
    }

    private fun parseLegacyDump(value: LuaValue): StackDump {
        val frames = readFrameArray(value)
        val current = CoroutineStackInfo("main", "running", frames, DEFAULT_FRAME_BASE, true)
        return StackDump(current, emptyList())
    }

    private fun parseCoroutineAwareDump(table: LuaTable): StackDump {
        val defaultFrameBase = table.get("frameBase").takeUnless { it.isnil() }?.toint() ?: DEFAULT_FRAME_BASE
        val current = parseCoroutine(table.get("current"), defaultFrameBase, isCurrent = true)
        val othersValue = table.get("coroutines")
        val others = when {
            othersValue.istable() -> readCoroutineList(othersValue.checktable(), defaultFrameBase)
            else -> emptyList()
        }
        return StackDump(current, others)
    }

    private fun readCoroutineList(table: LuaTable, defaultFrameBase: Int): List<CoroutineStackInfo> {
        val coroutines = mutableListOf<CoroutineStackInfo>()
        var i = 1
        while (true) {
            val entry = table.get(i)
            if (entry.isnil()) break
            coroutines.add(parseCoroutine(entry, defaultFrameBase, isCurrent = false))
            i++
        }
        return coroutines
    }

    private fun parseCoroutine(value: LuaValue, defaultFrameBase: Int, isCurrent: Boolean): CoroutineStackInfo {
        if (!value.istable()) {
            return CoroutineStackInfo(
                id = if (isCurrent) "main" else "thread",
                status = if (isCurrent) "running" else "unknown",
                frames = emptyList(),
                frameBase = defaultFrameBase,
                isCurrent = isCurrent
            )
        }

        val table = value.checktable()
        val id = table.get("id").takeUnless { it.isnil() }?.tojstring()
            ?: if (isCurrent) "main" else "thread"
        val status = table.get("status").takeUnless { it.isnil() }?.tojstring()
            ?: if (isCurrent) "running" else "unknown"
        val frameBase = table.get("frameBase").takeUnless { it.isnil() }?.toint() ?: defaultFrameBase
        val stackValue = table.get("stack")
        val frames = if (stackValue.isnil()) emptyList() else readFrameArray(stackValue)
        return CoroutineStackInfo(id, status, frames, frameBase, isCurrent)
    }

    private fun readFrameArray(value: LuaValue): List<FrameInfo> {
        if (!value.istable()) return emptyList()
        val table = value.checktable()
        val frames = mutableListOf<FrameInfo>()
        var i = 1
        while (true) {
            val frameTable = table.get(i)
            if (frameTable.isnil()) break
            frames.add(parseFrame(frameTable))
            i++
        }
        return frames
    }

    private fun parseFrame(frameTable: LuaValue): FrameInfo {
        val info = frameTable.get(IDX_INFO)
        val name = info.get(INFO_FUNCNAME).takeUnless { it.isnil() }?.tojstring() ?: "main"
        val source = info.get(INFO_SOURCE).takeUnless { it.isnil() }?.tojstring()
        val line = info.get(INFO_CURRENTLINE).takeUnless { it.isnil() }?.toint()

        val variables = buildList {
            addAll(elements = readVars(frameTable.get(IDX_LOCALS), kind = LOCAL))
            addAll(elements = readVars(frameTable.get(IDX_UPVALUES), kind = UPVALUE))
        }
        return FrameInfo(source, line, name, variables)
    }

    private fun readVars(value: LuaValue, kind: Kind): Sequence<MobVariable> {
        if (!value.istable()) return emptySequence()

        val table = value.checktable()
        val order = table.get(ORDER_KEY)
        val paramCount = when (kind) {
            LOCAL -> table.get(PARAM_COUNT_KEY).let { paramValue ->
                when {
                    paramValue.isint() || paramValue.isnumber() -> runCatching { paramValue.toint() }.getOrDefault(0)
                    else -> 0
                }
            }

            else -> 0
        }

        return when {
            order.istable() -> readOrderedVars(table, order.checktable(), kind, paramCount)
            else -> readUnorderedVars(table, kind)
        }
    }

    private fun readOrderedVars(
        table: LuaTable,
        orderTable: LuaTable,
        defaultKind: Kind,
        paramCount: Int
    ): Sequence<MobVariable> = generateSequence(1) { it + 1 }
        .map(orderTable::get)
        .takeWhile { !it.isnil() }
        .mapIndexedNotNull { index, nameValue ->
            val entry = table.get(nameValue)
            entry.takeUnless(LuaValue::isnil)?.let {
                val position = index + 1
                createVariable(nameValue, it, defaultKind, position, paramCount)
            }
        }

    private fun readUnorderedVars(table: LuaTable, defaultKind: Kind): Sequence<MobVariable> = table.keys()
        .asSequence()
        .filter { it.toStringSafely() !in setOf("__order", "__params") }
        .mapNotNull { key ->
            table.get(key)
                .takeUnless(LuaValue::isnil)
                ?.let { createVariable(key, it, defaultKind, Int.MAX_VALUE, 0) }
        }

    private fun createVariable(
        nameValue: LuaValue,
        entry: LuaValue,
        defaultKind: Kind,
        position: Int,
        paramCount: Int
    ): MobVariable {
        val name = nameValue.toStringSafely()
        val isParameter = paramCount > 0 && position in 1..paramCount
        val kind = if (isParameter) PARAMETER else defaultKind

        return MobVariable(
            name = name,
            value = MobRValue.fromLuaEntry(name, entry),
            expression = varargExpression(name),
            kind = kind
        )
    }
}
