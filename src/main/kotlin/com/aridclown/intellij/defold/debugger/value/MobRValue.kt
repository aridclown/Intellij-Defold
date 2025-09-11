package com.aridclown.intellij.defold.debugger.value

import com.intellij.icons.AllIcons
import org.luaj.vm2.*
import javax.swing.Icon

sealed class MobRValue {
    abstract val value: Any
    open val preview: String by lazy {
        try {
            value.toString()
        } catch (_: Throwable) {
            ""
        }
    }
    open val typeLabel: String? = null
    open val icon: Icon? = null
    open val hasChildren: Boolean = false

    sealed class MobRPrimitive : MobRValue() {
        override val hasChildren: Boolean = false
        override val icon: Icon = AllIcons.Debugger.Db_primitive
    }

    object Nil : MobRValue() {
        override val value: String = "nil"
        override val icon: Icon = AllIcons.Debugger.Db_primitive
    }

    data class Str(override val value: String) : MobRPrimitive() {
        override val typeLabel = "string"
        override val icon: Icon = AllIcons.Nodes.Constant
    }

    data class Num(override val value: String) : MobRPrimitive() {
        override val typeLabel = "number"
    }

    data class Bool(override val value: Boolean) : MobRPrimitive() {
        override val typeLabel = "boolean"
    }

    data class Table(override val value: String) : MobRValue() {
        override val typeLabel = "table"
        override val hasChildren = true
        override val icon: Icon = AllIcons.Debugger.Value
    }

    data class Func(override val value: String) : MobRValue() {
        override val typeLabel = "function"
        override val icon: Icon = AllIcons.Nodes.Function
    }

    data class Userdata(override val value: String) : MobRValue() {
        override val typeLabel = "userdata"
        override val icon: Icon = AllIcons.Nodes.DataTables
    }

    data class Thread(override val value: String) : MobRValue() {
        override val typeLabel = "thread"
        override val icon: Icon = AllIcons.Debugger.ThreadRunning
    }

    data class Unknown(override val value: String) : MobRValue() {
        override val typeLabel = null
        override val icon: Icon = AllIcons.Nodes.Unknown
    }

    companion object {
        fun fromLuaEntry(entry: LuaValue): MobRValue {
            // MobDebug may return a tuple table { raw, desc, ... }
            val (raw, desc) = when {
                entry.istable() -> {
                    val t: LuaTable = entry.checktable()
                    t.get(1) to t.get(2)
                }

                else -> entry to entry
            }

            return when (raw) {
                is LuaNil -> Nil
                is LuaNumber -> Num(raw.tojstring())
                is LuaString -> Str(raw.tojstring())
                is LuaBoolean -> Bool(raw.toboolean())
                is LuaTable -> Table(safeToString(desc))
                is LuaFunction -> Func(safeToString(desc))
                is LuaUserdata -> Userdata(safeToString(desc))
                is LuaThread -> Thread(safeToString(desc))
                else -> Unknown(safeToString(desc))
            }
        }

        fun fromRawLuaValue(raw: LuaValue): MobRValue = when {
            raw.isnil() -> Nil
            raw.isstring() -> Str(raw.tojstring())
            raw.isnumber() -> Num(raw.tojstring())
            raw.isboolean() -> Bool(raw.toboolean())
            raw.istable() -> Table(safeToString(raw))
            raw.isfunction() -> Func(safeToString(raw))
            raw.isuserdata() -> Userdata(safeToString(raw))
            raw.isthread() -> Thread(safeToString(raw))
            else -> Unknown(safeToString(raw))
        }

        private fun safeToString(v: LuaValue): String = try {
            v.tojstring()
        } catch (_: Throwable) {
            v.toString()
        }
    }
}
