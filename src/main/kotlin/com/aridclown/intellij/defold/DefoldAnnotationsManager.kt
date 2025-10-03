package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.util.SimpleHttpClient
import com.aridclown.intellij.defold.util.SimpleHttpClient.downloadToPath
import com.aridclown.intellij.defold.ui.NotificationService.notifyInfo
import com.aridclown.intellij.defold.ui.NotificationService.notifyWarning
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private const val DEFOLD_ANNOTATIONS_RESOURCE = "https://api.github.com/repos/astrochili/defold-annotations/releases"

/**
 * Downloads, caches and attaches Defold API annotations to SumnekoLua.
 * - Determines the target version from the project (fallback to latest)
 * - Downloads zip snapshot for the tag or main when not found
 * - Extracts only the `api/` directory into cache
 * - Creates .luarc.json file for SumnekoLua to automatically discover the API paths
 */
object DefoldAnnotationsManager {
    private val logger = Logger.getInstance(DefoldAnnotationsManager::class.java)

    suspend fun ensureAnnotationsAttached(project: Project, defoldVersion: String?) {
        // Run heavy work in the background to not block startup
        withBackgroundProgress(project, "Setting up Defold annotations", false) {
            try {
                val downloadUrl = resolveDownloadUrl(defoldVersion)
                val targetTag = extractTagFromUrl(downloadUrl)
                val targetDir = cacheDirForTag(targetTag)
                val apiDir = targetDir.resolve("defold_api")

                if (needsExtraction(apiDir)) {
                    downloadAndExtractApi(downloadUrl, targetDir)
                }

                // Create .luarc.json file for SumnekoLua to discover the API paths
                createLuarcConfiguration(project, apiDir)

                project.notifyInfo(
                    title = "Defold annotations ready",
                    content = "Configured SumnekoLua with Defold API ($targetTag) via .luarc.json"
                )
            } catch (e: Exception) {
                logger.warn("Failed to setup Defold annotations", e)
                project.notifyWarning(
                    title = "Defold annotations failed",
                    content = e.message ?: "Unknown error"
                )
            }
        }
    }

    private fun createLuarcConfiguration(project: Project, apiDir: Path) {
        val projectRoot = project.basePath?.let(Path::of) ?: return
        val luarcFile = projectRoot.resolve(".luarc.json")
        val luarcContent = generateLuarcContent(listOf(apiDir.toAbsolutePath().toString()))

        runCatching {
            Files.createDirectories(luarcFile.parent)
            Files.writeString(luarcFile, luarcContent)
        }.onFailure {
            println("Failed to create .luarc.json: ${it.message}")
        }
    }

    private fun generateLuarcContent(apiPaths: List<String>): String {
        val libraryPaths = apiPaths.joinToString(",\n        ") { "\"${it.replace("\\", "/")}\"" }

        return $$"""
        {
            "$schema": "https://raw.githubusercontent.com/LuaLS/vscode-lua/master/setting/schema.json",
            "workspace.library": [
                $$libraryPaths
            ],
            "workspace.checkThirdParty": false,
            "runtime.version": "Lua 5.1"
        }
        """.trimIndent()
    }

    private fun cacheDirForTag(tag: String): Path =
        Path.of(System.getProperty("user.home"), ".defold", "annotations", tag)
            .also(Files::createDirectories)

    private fun resolveDownloadUrl(defoldVersion: String?): String {
        val downloadUrl = when {
            defoldVersion.isNullOrBlank() -> "$DEFOLD_ANNOTATIONS_RESOURCE/latest"
            else -> "$DEFOLD_ANNOTATIONS_RESOURCE/tags/$defoldVersion"
        }

        return try {
            val json = SimpleHttpClient.get(downloadUrl).body
            val obj = JSONObject(json)
            val assets = obj.getJSONArray("assets")

            if (assets.length() == 0) throw Exception("No assets found in release")

            assets.getJSONObject(0)
                .getString("browser_download_url")
        } catch (e: Exception) {
            logger.error("Failed to fetch Defold annotations release asset url", e)
            throw Exception("Could not resolve Defold annotations download URL")
        }
    }

    private fun extractTagFromUrl(downloadUrl: String): String {
        val match = Regex("/download/([\\w.]+)/").find(downloadUrl)
        return match?.groups?.get(1)?.value ?: "unknown"
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

    private fun needsExtraction(apiDir: Path): Boolean {
        return when {
            Files.notExists(apiDir) -> true
            !Files.isDirectory(apiDir) -> true
            else -> Files.list(apiDir).use { stream -> !stream.findFirst().isPresent }
        }
    }
}
