package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.debugger.toStringSafely
import com.intellij.icons.AllIcons
import org.luaj.vm2.*
import javax.swing.Icon

data class MobVariable(
    val name: String,
    val value: MobRValue
)

sealed class MobRValue {
    abstract val content: Any
    open val preview: String get() = content.toString()
    open val typeLabel: String? = null
    open val icon: Icon? = null
    open val hasChildren: Boolean = false

    sealed class MobRPrimitive : MobRValue() {
        override val hasChildren: Boolean = false
        override val icon: Icon = AllIcons.Debugger.Db_primitive
    }

    object Nil : MobRValue() {
        override val content: String = "nil"
        override val icon: Icon = AllIcons.Debugger.Db_primitive
    }

    data class Str(override val content: String) : MobRPrimitive() {
        override val typeLabel = "string"
    }

    data class Num(override val content: String) : MobRPrimitive() {
        override val typeLabel = "number"
    }

    data class Bool(override val content: Boolean) : MobRPrimitive() {
        override val typeLabel = "boolean"
    }

    data class Table(
        override val content: String,
        val snapshot: LuaTable? = null,
    ) : MobRValue() {
        override val typeLabel = "table"
        override val hasChildren = true
        override val icon: Icon = AllIcons.Json.Object
    }

    data class Func(override val content: String) : MobRValue() {
        override val typeLabel = "function"
        override val icon: Icon = AllIcons.Nodes.Function
    }

    data class Hash(
        override val content: String,
        val value: String
    ) : MobRPrimitive() {
        override val typeLabel: String = "hash"
        override val preview: String = value

        companion object {
            private val regex = Regex("hash: \\[(.*)]")
            fun parse(desc: String): Hash? {
                val match = regex.matchEntire(desc.trim()) ?: return null
                val value = match.groupValues[1]
                return Hash(desc, value)
            }
        }
    }

    data class Url(
        override val content: String,
        val socket: String,
        val path: String?,
        val fragment: String?
    ) : MobRValue() {
        override val typeLabel: String = "url"
        override val hasChildren: Boolean = true
        override val icon: Icon = AllIcons.Json.Object
        override val preview: String = buildString {
            append(socket)
            append(":")
            path?.let { append(it) }
            fragment?.let { append("#").append(it) }
        }

        companion object {
            private val regex = Regex("url: \\[(.*)]")
            fun parse(desc: String): Url? {
                val match = regex.matchEntire(desc.trim()) ?: return null
                val raw = match.groupValues[1].trim().trim('"')
                val fragmentIdx = raw.indexOf('#')
                val fragment = if (fragmentIdx >= 0) raw.substring(fragmentIdx + 1) else null
                val base = if (fragmentIdx >= 0) raw.substring(0, fragmentIdx) else raw
                val colonIdx = base.indexOf(':')
                val socket = if (colonIdx >= 0) base.substring(0, colonIdx) else ""
                val path = if (colonIdx >= 0) base.substring(colonIdx + 1).takeIf { it.isNotEmpty() } else null
                return Url(desc, socket, path, fragment)
            }
        }
    }

    data class Message(
        override val content: String,
        val id: String
    ) : MobRPrimitive() {
        override val typeLabel: String = "message"
        override val preview: String = id

        companion object {
            private val regex = Regex("message: \\[(.*)]")
            fun parse(desc: String): Message? {
                val match = regex.matchEntire(desc.trim()) ?: return null
                val id = match.groupValues[1]
                return Message(desc, id)
            }
        }
    }

    data class Vector(
        override val content: String,
        val components: List<Double>
    ) : MobRValue() {
        override val typeLabel: String = "vector${components.size}"
        override val hasChildren: Boolean = true
        override val icon: Icon = AllIcons.Json.Array
        override val preview: String = components.joinToString(", ", "(", ")")

        companion object {
            private val regex = Regex("vmath\\.vector(\\d)\\(([^)]*)\\)")
            fun parse(desc: String): Vector? {
                val match = regex.matchEntire(desc.trim()) ?: return null
                val dim = match.groupValues[1].toInt()
                val comps = match.groupValues[2]
                    .split(',')
                    .mapNotNull { it.trim().toDoubleOrNull() }
                if (comps.size != dim) return null
                return Vector(desc, comps)
            }
        }
    }

    data class Quat(
        override val content: String,
        val components: List<Double>
    ) : MobRValue() {
        override val typeLabel: String = "quat"
        override val hasChildren: Boolean = true
        override val icon: Icon = AllIcons.Json.Array
        override val preview: String = components.joinToString(", ", "(", ")")

        companion object {
            private val regex = Regex("vmath\\.quat\\(([^)]*)\\)")
            fun parse(desc: String): Quat? {
                val match = regex.matchEntire(desc.trim()) ?: return null
                val comps = match.groupValues[1]
                    .split(',')
                    .mapNotNull { it.trim().toDoubleOrNull() }
                if (comps.size != 4) return null
                return Quat(desc, comps)
            }
        }
    }

    data class Matrix(
        override val content: String,
        val rows: List<List<Double>>
    ) : MobRValue() {
        override val typeLabel: String = "matrix4"
        override val hasChildren: Boolean = true
        override val icon: Icon = AllIcons.Json.Array
        override val preview: String = rows.joinToString(", ", "[", "]") {
            it.joinToString(", ", "(", ")")
        }

        companion object {
            private val regex = Regex("vmath\\.matrix4\\(([^)]*)\\)")
            fun parse(desc: String): Matrix? {
                val match = regex.matchEntire(desc.trim()) ?: return null
                val comps = match.groupValues[1]
                    .split(',')
                    .mapNotNull { it.trim().toDoubleOrNull() }
                if (comps.size != 16) return null
                val rows = comps.chunked(4)
                return Matrix(desc, rows)
            }
        }
    }

    data class Userdata(override val content: String) : MobRValue() {
        override val typeLabel = "userdata"
        override val icon: Icon = AllIcons.Nodes.DataTables
    }

    data class Thread(override val content: String) : MobRValue() {
        override val typeLabel = "thread"
        override val icon: Icon = AllIcons.Debugger.ThreadRunning
    }

    data class Unknown(override val content: String) : MobRValue() {
        override val typeLabel = null
        override val icon: Icon = AllIcons.Nodes.Unknown
    }

    companion object {
        fun fromLuaEntry(entry: LuaValue): MobRValue {
            // MobDebug may return a tuple table { raw, desc, ... }
            val (raw, desc) = when {
                entry.istable() -> {
                    val table = entry.checktable()
                    table.get(1) to table.get(2)
                }

                else -> entry to entry
            }

            return fromLuaValue(raw, desc)
        }

        fun fromRawLuaValue(raw: LuaValue): MobRValue = fromLuaValue(raw, raw)

        private fun fromLuaValue(raw: LuaValue, desc: LuaValue): MobRValue {
            val safeDesc = desc.toStringSafely()

            return when (raw) {
                is LuaNil -> Nil
                is LuaNumber -> Num(raw.tojstring())
                is LuaString -> parseUserdata(safeDesc) ?: Str(raw.tojstring())
                is LuaBoolean -> Bool(raw.toboolean())
                is LuaTable -> Table(safeDesc, raw)
                is LuaFunction -> Func(safeDesc)
                is LuaThread -> Thread(safeDesc)
                is LuaUserdata -> parseUserdata(safeDesc) ?: Userdata(safeDesc)
                else -> Unknown(safeDesc)
            }
        }

        private val userdataParsers = listOf<(String) -> MobRValue?>(
            Vector::parse,
            Quat::parse,
            Matrix::parse,
            Hash::parse,
            Url::parse,
            Message::parse,
        )

        private fun parseUserdata(desc: String): MobRValue? {
            for (parser in userdataParsers) {
                val rv = parser(desc)
                if (rv != null) return rv
            }
            return null
        }
    }
}
