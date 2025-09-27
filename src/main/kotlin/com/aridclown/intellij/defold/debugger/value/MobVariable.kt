package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.DefoldConstants.VARARG_PREVIEW_LIMIT
import com.aridclown.intellij.defold.debugger.lua.LuaExprUtil.child
import com.aridclown.intellij.defold.debugger.toStringSafely
import com.aridclown.intellij.defold.util.letIf
import com.intellij.icons.AllIcons
import org.luaj.vm2.*
import javax.swing.Icon

data class MobVariable(
    val name: String,
    val value: MobRValue,
    val expression: String = name
)

sealed class MobRValue {
    abstract val content: Any
    open val preview: String get() = content.toString()
    open val typeLabel: String? = null
    open val icon: Icon? = null
    open val hasChildren: Boolean = false

    data class VarargPreview(
        private val entries: List<MobVariable>,
    ) : MobRValue() {
        override val content: Any = entries
        override val typeLabel: String = "vararg"
        override val icon: Icon = AllIcons.Json.Array

        override val preview: String = entries
            .take(VARARG_PREVIEW_LIMIT)
            .joinToString(", ") { it.value.preview }
            .letIf(entries.size > VARARG_PREVIEW_LIMIT) { base ->
                if (base.isEmpty()) "…" else "$base, …"
            }
            .ifBlank { "—" }
    }

    sealed class MobRPrimitive : MobRValue() {
        override val hasChildren: Boolean = false
        override val icon: Icon = AllIcons.Debugger.Db_primitive
    }

    sealed class DefoldObject : MobRValue() {
        override val hasChildren: Boolean = true
        override val icon: Icon = AllIcons.Json.Object
    }

    sealed class Vector(
        override val content: String,
        open val components: List<Double>
    ) : DefoldObject() {
        override val hasChildren: Boolean = true
        override val icon: Icon = AllIcons.Json.Array
        override val preview: String by lazy { components.joinToString(", ", "(", ")") }

        fun toMobVarList(baseExpr: String): List<MobVariable> = listOf("x", "y", "z", "w")
            .take(components.size)
            .mapIndexed { index, name ->
                val num = Num(components[index].toString())
                MobVariable(name, num, child(baseExpr, name))
            }
    }

    object Nil : MobRValue() {
        override val content: String = "nil"
        override val icon: Icon = AllIcons.Debugger.Db_primitive
    }

    data class Str(override val content: String) : MobRValue() {
        override val typeLabel = "string"
        override val icon: Icon = AllIcons.FileTypes.Text
    }

    data class Num(override val content: String) : MobRPrimitive() {
        override val typeLabel = "number"
    }

    data class Bool(override val content: Boolean) : MobRPrimitive() {
        override val typeLabel = "boolean"
    }

    open class Table(
        override val content: String = "table",
        open val snapshot: LuaTable? = null,
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
        override val icon: Icon = AllIcons.Nodes.Tag

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
    ) : DefoldObject() {
        override val typeLabel: String = "url"
        override val icon: Icon = AllIcons.Nodes.Related
        override val preview: String = buildString {
            append(socket)
            append(":")
            path?.let(::append)
            fragment?.let(append("#")::append)
        }

        fun toMobVarList(baseExpr: String) = buildList {
            add(MobVariable("socket", Str(socket), child(baseExpr, "socket")))
            path?.let {
                add(MobVariable("path", Str(it), child(baseExpr, "path")))
            }
            fragment?.let {
                add(MobVariable("fragment", Str(it), child(baseExpr, "fragment")))
            }
        }

        companion object {
            private val regex = Regex("url: \\[(.*)]")

            fun parse(desc: String): Url? {
                val match = regex.matchEntire(desc.trim()) ?: return null
                val raw = match.groupValues[1].trim().trim('"')

                val base = raw.substringBefore('#')
                val fragment = raw.substringAfter('#', "").ifBlank { null }

                val colonIdx = base.indexOf(':')
                val socket = if (colonIdx >= 0) base.take(colonIdx) else ""
                val path = if (colonIdx >= 0) base.substring(colonIdx + 1).ifBlank { null } else null

                return Url(desc, socket, path, fragment)
            }
        }
    }

    data class ScriptInstance(
        override val content: String,
        val type: Type,
        val identity: String
    ) : DefoldObject() {
        override val typeLabel: String = type.label
        override val hasChildren: Boolean = true
        override val icon: Icon = AllIcons.Ide.ConfigFile
        override val preview: String = identity

        enum class Type(val label: String) {
            GAME_OBJECT("script"),
            GUI("gui script"),
            RENDER("render script")
        }

        companion object {
            private val regex = Regex("^(Script|GuiScript|RenderScript):\\s*(.*)$")

            fun parse(desc: String): ScriptInstance? {
                val match = regex.matchEntire(desc.trim()) ?: return null
                val type = when (match.groupValues[1]) {
                    "Script" -> Type.GAME_OBJECT
                    "GuiScript" -> Type.GUI
                    "RenderScript" -> Type.RENDER
                    else -> return null
                }
                val identity = match.groupValues[2].ifBlank { match.groupValues[1] }
                return ScriptInstance(desc, type, identity)
            }
        }
    }

    data class Message(
        override val content: String,
        override val snapshot: LuaTable? = null,
    ) : Table() {
        override val typeLabel: String = "message"
        override val icon: Icon = AllIcons.Webreferences.MessageQueue
    }

    data class VectorN(
        override val content: String,
        override val components: List<Double>
    ) : Vector(content, components) {
        override val typeLabel: String = "vector${components.size}"

        companion object {
            private val regex = Regex("vmath\\.vector(\\d)\\(([^)]*)\\)")
            fun parse(desc: String): VectorN? {
                val match = regex.matchEntire(desc.trim()) ?: return null
                val dim = match.groupValues[1].toInt()
                val comps = match.groupValues[2]
                    .split(',')
                    .mapNotNull { it.trim().toDoubleOrNull() }

                if (comps.size != dim) return null
                return VectorN(desc, comps)
            }
        }
    }

    data class Quat(
        override val content: String,
        override val components: List<Double>
    ) : Vector(content, components) {
        override val typeLabel: String = "quat"

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
    ) : DefoldObject() {
        override val typeLabel: String = "matrix4"
        override val hasChildren: Boolean = true
        override val icon: Icon = AllIcons.Json.Array
        override val preview: String = rows.joinToString(", ", "[", "]") {
            it.joinToString(", ", "(", ")")
        }

        fun toMobVarList() = rows
            .mapIndexed { index, row ->
                val rowName = "row${index + 1}"
                MobVariable(rowName, VectorN("", row), "")
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
        override val hasChildren: Boolean = false
    }

    data class Thread(override val content: String) : MobRValue() {
        override val typeLabel = "thread"
        override val icon: Icon = AllIcons.Debugger.VariablesTab
    }

    data class Unknown(override val content: String) : MobRValue() {
        override val typeLabel = null
        override val icon: Icon = AllIcons.Nodes.Unknown
    }

    companion object {
        fun fromLuaEntry(name: String, entry: LuaValue): MobRValue {
            val raw = when {
                entry.istable() -> entry.checktable().get(1)
                else -> entry
            }

            return fromLuaValue(name, raw)
        }

        fun fromRawLuaValue(name: String, raw: LuaValue): MobRValue = fromLuaValue(name, raw)

        private fun fromLuaValue(name: String, raw: LuaValue): MobRValue {
            val desc = raw.toStringSafely()

            return when (raw) {
                is LuaNil -> Nil
                is LuaNumber -> Num(raw.tojstring())
                is LuaString -> parseDefoldUserdata(desc) ?: Str(raw.tojstring())
                is LuaBoolean -> Bool(raw.toboolean())
                is LuaTable -> parseTable(name, desc, raw)
                is LuaFunction -> Func(desc)
                is LuaThread -> Thread(desc)
                is LuaUserdata -> parseDefoldUserdata(desc) ?: Userdata(desc)
                else -> Unknown(desc)
            }
        }

        /**
         * Parse LuaTable into appropriate MobRValue based on variable name and content.
         * Messages are identified by variable name, other special tables by content patterns.
         */
        private fun parseTable(name: String, desc: String, table: LuaTable): MobRValue {
            // Special case: Messages are identified by variable name "message" 
            if (name.equals("message", ignoreCase = true)) {
                return Message(desc, table)
            }

            // Default: regular table
            return Table(desc, table)
        }

        private val defoldUserDataParsers = listOf<(String) -> MobRValue?>(
            VectorN::parse,
            Quat::parse,
            Matrix::parse,
            ScriptInstance::parse,
            Hash::parse,
            Url::parse
        )

        private fun parseDefoldUserdata(desc: String): MobRValue? {
            for (parser in defoldUserDataParsers) {
                val rv = parser(desc)
                if (rv != null) return rv
            }
            return null
        }
    }
}
