package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.debugger.lua.child
import com.aridclown.intellij.defold.debugger.lua.toStringSafely
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

/**
 * Pure helpers to sort table keys and build child entries for a Lua table.
 */
object TableChildrenPager {
    data class ChildEntry(
        val name: String,
        val rvalue: MobRValue,
        val expr: String
    )

    /**
     * Sorts keys so numbers come first, then by their string value.
     */
    fun sortedKeys(table: LuaTable): List<LuaValue> =
        table.keys().toList().sortedWith(compareBy({ !it.isnumber() }, { it.tojstring() }))

    /**
     * Builds child entries for a slice [from, to) over the given sorted keys.
     */
    fun buildSlice(
        baseExpr: String,
        table: LuaTable,
        sortedKeys: List<LuaValue>,
        from: Int,
        to: Int
    ): List<ChildEntry> = buildList {
        for (i in from until to) {
            val k = sortedKeys[i]
            val childName = k.toStringSafely()
            val rv = MobRValue.fromRawLuaValue(childName, table.get(k))
            val childExpr = child(baseExpr, childName)
            add(ChildEntry(childName, rv, childExpr))
        }
    }
}
