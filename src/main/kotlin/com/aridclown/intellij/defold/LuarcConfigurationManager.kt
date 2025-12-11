package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldVersion
import com.aridclown.intellij.defold.util.NotificationService.notifyInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString
import java.util.LinkedHashMap

class LuarcConfigurationManager {
    private val logger = Logger.getInstance(LuarcConfigurationManager::class.java)

    fun ensureConfiguration(
        project: Project,
        apiDir: Path
    ) {
        val projectRoot = project.basePath?.let(Path::of) ?: return
        val luarcFile = projectRoot.resolve(".luarc.json")
        val apiPath = apiDir.toAbsolutePath().normalize().pathString

        runCatching {
            val configurationChanged = if (Files.exists(luarcFile)) {
                updateExistingConfiguration(luarcFile, apiPath)
            } else {
                createConfigurationFile(luarcFile, apiPath)
                true
            }

            if (configurationChanged) {
                LocalFileSystem.getInstance().refreshNioFiles(listOf(luarcFile))
                val versionLabel = project.defoldVersion?.takeUnless { version -> version.isBlank() } ?: "latest"
                project.notifyInfo(
                    title = "Defold annotations ready",
                    content = "Configured LuaLS for Defold API $versionLabel via .luarc.json"
                )
            }
        }.onFailure {
            logger.warn("Failed to create .luarc.json: ${it.message}", it)
        }
    }

    fun generateContent(apiPath: String): String {
        val libraryPath = "\"${Path.of(apiPath).normalize().pathString}\""
        val extensions = DefoldScriptType.entries.joinToString(", ") { "\".${it.extension}\"" }
        val schemaKey = SCHEMA_KEY

        return """
            {
                "$schemaKey": "$LUA_LS_SCHEMA",
                "workspace": {
                    "library": [ $libraryPath ],
                    "checkThirdParty": false,
                    "ignoreDir": [ "debugger" ]
                },
                "runtime": {
                    "version": "$LUA_RUNTIME_VERSION",
                    "extensions": [ $extensions ]
                }
            }
        """.trimIndent()
    }

    private fun createConfigurationFile(luarcFile: Path, apiPath: String) {
        luarcFile.parent?.let(Files::createDirectories)
        val content = JSONObject(generateContent(apiPath)).toOrderedString(pretty = true)
        Files.writeString(luarcFile, content)
    }

    private fun updateExistingConfiguration(luarcFile: Path, apiPath: String): Boolean {
        val desired = JSONObject(generateContent(apiPath))
        val content = runCatching { Files.readString(luarcFile) }.getOrNull()
        val existing = content
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: JSONObject()

        mergeJson(existing, desired)
        val output = existing.toOrderedString(pretty = true)

        if (content == output) {
            return false
        }

        Files.writeString(luarcFile, output)
        return true
    }

    private fun mergeJson(target: JSONObject, desired: JSONObject): Boolean {
        var changed = false

        desired.keys().forEachRemaining { key ->
            when (val desiredValue = desired.get(key)) {
                is JSONObject -> {
                    val targetValue = target.optJSONObject(key)
                    if (targetValue == null) {
                        target.put(key, desiredValue.copyJson())
                        changed = true
                    } else if (mergeJson(targetValue, desiredValue)) {
                        changed = true
                    }
                }

                is JSONArray -> {
                    val targetArray = target.optJSONArray(key)
                    if (targetArray == null) {
                        target.put(key, desiredValue.copyArray())
                        changed = true
                    } else if (mergeArray(targetArray, desiredValue)) {
                        changed = true
                    }
                }

                else -> {
                    val shouldReplace =
                        !target.has(key) || (key == SCHEMA_KEY && target.optString(key) != desiredValue.toString())
                    if (shouldReplace) {
                        target.put(key, desiredValue)
                        changed = true
                    }
                }
            }
        }

        return changed
    }

    private fun mergeArray(target: JSONArray, desired: JSONArray): Boolean {
        var changed = false
        for (index in 0 until desired.length()) {
            val value = desired.get(index)
            if (!target.containsValue(value)) {
                target.put(value)
                changed = true
            }
        }
        return changed
    }

    private fun JSONArray.containsValue(value: Any?): Boolean {
        for (index in 0 until length()) {
            if (valuesEqual(get(index), value)) {
                return true
            }
        }
        return false
    }

    private fun valuesEqual(first: Any?, second: Any?): Boolean {
        if (first == null && second == null) return true
        if (first == null || second == null) return false

        return when (first) {
            is JSONObject if second is JSONObject -> first.similar(second)
            is JSONArray if second is JSONArray -> first.toString() == second.toString()
            else -> first == second
        }
    }

    private fun JSONObject.copyJson(): JSONObject = JSONObject(this.toString())

    private fun JSONArray.copyArray(): JSONArray = JSONArray(this.toString())

    private fun JSONObject.toOrderedString(pretty: Boolean = false): String {
        val ordered = LinkedHashMap<String, Any?>()
        if (has(SCHEMA_KEY)) {
            ordered[SCHEMA_KEY] = get(SCHEMA_KEY)
        }
        keys().forEachRemaining { key ->
            if (key != SCHEMA_KEY) {
                ordered[key] = get(key)
            }
        }

        val orderedJson = JSONObject(ordered)
        return if (pretty) orderedJson.toString(4) else orderedJson.toString()
    }

    companion object {
        private const val LUA_LS_SCHEMA =
            "https://raw.githubusercontent.com/LuaLS/vscode-lua/master/setting/schema.json"
        private const val LUA_RUNTIME_VERSION = "Lua 5.1"
        private const val SCHEMA_KEY = $$"$schema"
    }
}
