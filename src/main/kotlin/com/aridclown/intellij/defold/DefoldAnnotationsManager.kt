package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.process.DefoldCoroutineService.Companion.launch
import com.aridclown.intellij.defold.ui.NotificationService.notify
import com.aridclown.intellij.defold.ui.NotificationService.notifyInfo
import com.aridclown.intellij.defold.ui.NotificationService.notifyWarning
import com.aridclown.intellij.defold.util.SimpleHttpClient
import com.aridclown.intellij.defold.util.SimpleHttpClient.downloadToPath
import com.aridclown.intellij.defold.util.stdLibraryRoot
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration.ofSeconds
import java.util.zip.ZipInputStream
import kotlin.io.path.pathString

private const val DEFOLD_ANNOTATIONS_RESOURCE = "https://api.github.com/repos/astrochili/defold-annotations/releases"

/**
 * Downloads, caches and attaches Defold API annotations to LuaLS.
 * - Determines the target version from the project (fallback to latest)
 * - Downloads zip snapshot for the tag or main when not found
 * - Extracts only the `api/` directory into cache
 * - Creates `.luarc.json` file for LuaLS to automatically discover the API paths
 */
object DefoldAnnotationsManager {
    private val logger = Logger.getInstance(DefoldAnnotationsManager::class.java)

    suspend fun ensureAnnotationsAttached(project: Project, defoldVersion: String?) {
        val targetDir = cacheDirForTag(defoldVersion)
        val apiDir = targetDir.resolve("defold_api")
        val needsExtraction = targetDir.needsExtraction()

        if (needsExtraction) {
            withBackgroundProgress(project, "Setting up Defold annotations", false) {
                runCatching {
                    downloadAndExtractApi(downloadUrl = resolveDownloadUrl(defoldVersion), targetDir)
                    refreshAnnotationsRoot(targetDir, apiDir)
                }.onSuccess { targetTag ->
                    project.notifyInfo(
                        title = "Defold annotations ready",
                        content = "Configured LuaLS for Defold API $targetTag via .luarc.json"
                    )
                }.onFailure { error ->
                    handleAnnotationsFailure(project, defoldVersion, error)
                }
            }
        }

        ensureLuarcConfiguration(project, apiDir)
    }

    private fun handleAnnotationsFailure(project: Project, defoldVersion: String?, error: Throwable) {
        if (error is UnknownHostException) {
            project.notify(
                title = "Defold annotations failed",
                content = "Failed to download Defold annotations. Verify your connection, proxy, and firewall settings before trying again.",
                type = WARNING,
                actions = listOf(NotificationAction.createSimpleExpiring("Retry") {
                    project.launch { ensureAnnotationsAttached(project, defoldVersion) }
                })
            )
            return
        }

        logger.warn("Failed to setup Defold annotations", error)
        project.notifyWarning(
            title = "Defold annotations failed",
            content = error.message ?: "Unknown error"
        )
    }

    private fun refreshAnnotationsRoot(targetDir: Path, apiDir: Path) =
        LocalFileSystem.getInstance().refreshNioFiles(listOf(targetDir, apiDir))

    private fun ensureLuarcConfiguration(project: Project, apiDir: Path) {
        val projectRoot = project.basePath?.let(Path::of) ?: return
        val luarcFile = projectRoot.resolve(".luarc.json")
        val luarcContent = generateLuarcContent(apiDir.toAbsolutePath().pathString)

        if (Files.exists(luarcFile)) return

        runCatching {
            Files.createDirectories(luarcFile.parent)
            Files.writeString(luarcFile, luarcContent)
            LocalFileSystem.getInstance().refreshNioFiles(listOf(luarcFile))
        }.onFailure {
            println("Failed to create .luarc.json: ${it.message}")
        }
    }

    private fun generateLuarcContent(apiPath: String): String {
        val libraryPath = "\"${Path.of(apiPath).normalize().pathString}\""
        val extensions = DefoldScriptType.entries.joinToString(", ") { "\".${it.extension}\"" }

        return $$"""
        {
            "$schema": "https://raw.githubusercontent.com/LuaLS/vscode-lua/master/setting/schema.json",
            "workspace.library": [ $$libraryPath ],
            "workspace.checkThirdParty": false,
            "runtime": {
                "version": "Lua 5.1",
                "extensions": [ $$extensions ]
            }
        }
        """.trimIndent()
    }

    private fun cacheDirForTag(tag: String?): Path {
        val actualTag = tag?.takeUnless { it.isBlank() } ?: "latest"
        return stdLibraryRoot().resolve(actualTag).also(Files::createDirectories)
    }

    private fun resolveDownloadUrl(defoldVersion: String?): String {
        val downloadUrl = when {
            defoldVersion.isNullOrBlank() -> "$DEFOLD_ANNOTATIONS_RESOURCE/latest"
            else -> "$DEFOLD_ANNOTATIONS_RESOURCE/tags/$defoldVersion"
        }

        return try {
            val json = SimpleHttpClient.get(downloadUrl, ofSeconds(10)).body
            val obj = JSONObject(json)
            val assets = obj.getJSONArray("assets")

            if (assets.length() == 0) throw Exception("No assets found in release")

            assets.getJSONObject(0)
                .getString("browser_download_url")
        } catch (e: UnknownHostException) {
            throw e
        } catch (e: InterruptedIOException) {
            throw Exception("Could not resolve Defold annotations due to timeout", e)
        } catch (e: Exception) {
            logger.error("Failed to fetch Defold annotations release asset url", e)
            throw Exception("Could not resolve Defold annotations download URL", e)
        }
    }

    private fun downloadAndExtractApi(downloadUrl: String, targetDir: Path) {
        val tmpZip = Files.createTempFile("defold-annotations-", ".zip")
        try {
            downloadToPath(downloadUrl, tmpZip)
            unzipApiFileToDest(tmpZip, targetDir)
        } finally {
            try {
                Files.deleteIfExists(tmpZip)
            } catch (_: Exception) {
                logger.error("Failed to delete temp file $tmpZip")
            }
        }
    }

    private fun unzipApiFileToDest(zipFile: Path, destDir: Path) {
        ZipInputStream(Files.newInputStream(zipFile)).use { zis ->
            generateSequence { zis.nextEntry }.forEach { entry ->
                val outPath = destDir.resolve(entry.name)
                if (entry.isDirectory) {
                    Files.createDirectories(outPath)
                } else {
                    outPath.parent?.let(Files::createDirectories)
                    Files.newOutputStream(outPath).use { output ->
                        zis.copyTo(output)
                    }
                }
                zis.closeEntry()
            }
        }
    }

    private fun Path.needsExtraction(): Boolean = when {
        Files.notExists(this) -> true
        !Files.isDirectory(this) -> true
        else -> Files.list(this).use { it.findFirst().isEmpty }
    }
}
