package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldVersion
import com.aridclown.intellij.defold.util.NotificationService.notifyInfo
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

class LuarcConfigurationManager {
    private val logger = Logger.getInstance(LuarcConfigurationManager::class.java)
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun ensureConfiguration(project: Project, apiDir: Path) {
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

    fun generateContent(apiPath: String): LuarcConfig {
        val normalizedPath = Path.of(apiPath).normalize().pathString
        val extensions = DefoldScriptType.entries.map { ".${it.extension}" }

        return LuarcConfig(
            schema = LUA_LS_SCHEMA,
            workspace = WorkspaceConfig(
                library = listOf(normalizedPath),
                checkThirdParty = false,
                ignoreDir = listOf("debugger")
            ),
            runtime = RuntimeConfig(
                version = LUA_RUNTIME_VERSION,
                extensions = extensions
            )
        )
    }

    private fun createConfigurationFile(luarcFile: Path, apiPath: String) {
        luarcFile.parent?.let(Files::createDirectories)
        val config = generateContent(apiPath)
        Files.writeString(luarcFile, toJson(config))
    }

    private fun updateExistingConfiguration(luarcFile: Path, apiPath: String): Boolean {
        val desired = generateContent(apiPath)
        val existing = runCatching {
            JsonParser.parseString(Files.readString(luarcFile)).asJsonObject
        }.getOrElse { JsonObject() }

        val merged = mergeConfigs(existing, desired)
        val output = toJson(merged)

        if (Files.exists(luarcFile) && Files.readString(luarcFile) == output) {
            return false
        }

        Files.writeString(luarcFile, output)
        return true
    }

    private fun mergeConfigs(existing: JsonObject, desired: LuarcConfig) = existing.deepCopy().apply {
        addProperty(SCHEMA_KEY, desired.schema)

        val workspace = getAsJsonObject("workspace")
            ?: JsonObject().also { add("workspace", it) }

        val librarySet = workspace.getAsJsonArray("library").mapNotEmpty()
        workspace.add("library", gson.toJsonTree(librarySet + desired.workspace.library))
        workspace.addProperty("checkThirdParty", desired.workspace.checkThirdParty)

        val ignoreDirSet = workspace.getAsJsonArray("ignoreDir").mapNotEmpty()
        workspace.add("ignoreDir", gson.toJsonTree(ignoreDirSet + desired.workspace.ignoreDir))

        val runtime = getAsJsonObject("runtime")
            ?: JsonObject().also { add("runtime", it) }

        runtime.addProperty("version", desired.runtime.version)

        val extensionSet = runtime.getAsJsonArray("extensions").mapNotEmpty()
        runtime.add("extensions", gson.toJsonTree(extensionSet + desired.runtime.extensions))
    }

    private fun JsonArray?.mapNotEmpty() = this?.mapNotNull { it.takeIf { !it.isJsonNull }?.asString }
        ?.toSet()
        ?: emptySet()

    private fun toJson(config: Any): String {
        val json = gson.toJsonTree(config).asJsonObject
        val ordered = JsonObject()

        json.get(SCHEMA_KEY)?.let { ordered.add(SCHEMA_KEY, it) }
        json.entrySet().forEach { (key, value) ->
            if (key != SCHEMA_KEY) {
                ordered.add(key, value)
            }
        }

        return gson.toJson(ordered)
    }

    data class LuarcConfig(
        @SerializedName($$"$schema")
        val schema: String,
        val workspace: WorkspaceConfig,
        val runtime: RuntimeConfig
    )

    data class WorkspaceConfig(
        val library: List<String>,
        val checkThirdParty: Boolean,
        val ignoreDir: List<String>
    )

    data class RuntimeConfig(
        val version: String,
        val extensions: List<String>
    )

    companion object {
        private const val LUA_LS_SCHEMA =
            "https://raw.githubusercontent.com/LuaLS/vscode-lua/master/setting/schema.json"
        private const val LUA_RUNTIME_VERSION = "Lua 5.1"
        private const val SCHEMA_KEY = $$"$schema"
    }
}
