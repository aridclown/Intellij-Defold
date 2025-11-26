package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldVersion
import com.aridclown.intellij.defold.util.NotificationService.notifyInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

class LuarcConfigurationManager {
    private val logger = Logger.getInstance(LuarcConfigurationManager::class.java)

    fun ensureConfiguration(
        project: Project,
        apiDir: Path
    ) {
        val projectRoot = project.basePath?.let(Path::of) ?: return
        val luarcFile = projectRoot.resolve(".luarc.json")

        if (Files.exists(luarcFile)) return

        val luarcContent = generateContent(apiDir.toAbsolutePath().pathString)

        runCatching {
            Files.createDirectories(luarcFile.parent)
            Files.writeString(luarcFile, luarcContent)
            LocalFileSystem.getInstance().refreshNioFiles(listOf(luarcFile))
        }.onSuccess {
            val versionLabel = project.defoldVersion?.takeUnless { version -> version.isBlank() } ?: "latest"
            project.notifyInfo(
                title = "Defold annotations ready",
                content = "Configured LuaLS for Defold API $versionLabel via .luarc.json"
            )
        }.onFailure {
            logger.warn("Failed to create .luarc.json: ${it.message}", it)
        }
    }

    fun generateContent(apiPath: String): String {
        val libraryPath = "\"${Path.of(apiPath).normalize().pathString}\""
        val extensions = DefoldScriptType.entries.joinToString(", ") { "\".${it.extension}\"" }
        val schemaKey = "${'$'}schema"

        return """
            {
                "$schemaKey": "https://raw.githubusercontent.com/LuaLS/vscode-lua/master/setting/schema.json",
                "workspace": {
                    "library": [ $libraryPath ],
                    "checkThirdParty": false,
                    "ignoreDir": [ "debugger" ]
                },
                "runtime": {
                    "version": "Lua 5.1",
                    "extensions": [ $extensions ]
                }
            }
        """.trimIndent()
    }
}
