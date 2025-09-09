package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.ui.NotificationService
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.blockingContextScope
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Downloads, caches and attaches Defold API annotations to SumnekoLua.
 * - Determines the target version from the project (fallback to latest)
 * - Downloads zip snapshot for the tag or main when not found
 * - Extracts only the `api/` directory into cache
 * - Creates .luarc.json file for SumnekoLua to automatically discover the API paths
 */
object DefoldAnnotationsManager {
    private val log = Logger.getInstance(DefoldAnnotationsManager::class.java)

    suspend fun ensureAnnotationsAttached(project: Project, defoldVersion: String?) {
        // Run heavy work in the background to not block startup
//        curren    tThreadCoroutineScope().launch {
            withBackgroundProgress(project, "Setting up Defold annotations", false) { ->
                try {
                    val downloadUrl = resolveDownloadUrl(defoldVersion)
                    val targetTag = extractTagFromUrl(downloadUrl)
                    val targetDir = cacheDirForTag(targetTag)
                    val apiDir = targetDir.resolve("defold_api").toFile()

                    if (!apiDir.exists() || apiDir.listFiles()?.isEmpty() != false) {
                        downloadAndExtractApi(downloadUrl, targetDir)
                    }

                    // Create .luarc.json file for SumnekoLua to discover the API paths
                    createLuarcConfiguration(project, apiDir)

                    NotificationService.notify(
                        project,
                        "Defold annotations ready",
                        "Configured SumnekoLua with Defold API ($targetTag) via .luarc.json",
                        INFORMATION
                    )
                } catch (e: Exception) {
                    log.warn("Failed to setup Defold annotations", e)
                    NotificationService.notify(
                        project,
                        "Defold annotations failed",
                        e.message ?: "Unknown error",
                        WARNING
                    )
                }
            }
//        }
    }

    private fun createLuarcConfiguration(project: Project, apiDir: File) {
        val projectRoot = project.basePath ?: return
        val luarcFile = File(projectRoot, ".luarc.json")
        val luarcContent = generateLuarcContent(listOf(apiDir.absolutePath))

        try {
            luarcFile.writeText(luarcContent)
        } catch (e: Exception) {
            println("Failed to create .luarc.json: ${e.message}")
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

    private fun cacheDirForTag(tag: String): Path {
        val base = Path.of(System.getProperty("user.home"), ".defold", "annotations", tag)
        Files.createDirectories(base)
        return base
    }

    private fun resolveDownloadUrl(defoldVersion: String?): String {
        val apiUrl = if (defoldVersion.isNullOrBlank()) {
            "https://api.github.com/repos/astrochili/defold-annotations/releases/latest"
        } else {
            "https://api.github.com/repos/astrochili/defold-annotations/releases/tags/$defoldVersion"
        }

        return try {
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            conn.inputStream.bufferedReader().use { reader ->
                val json = reader.readText()
                val obj = JSONObject(json)
                val assets = obj.getJSONArray("assets")

                if (assets.length() == 0) throw Exception("No assets found in release")

                assets.getJSONObject(0)
                    .getString("browser_download_url")
            }
        } catch (e: Exception) {
            log.error("Failed to fetch Defold annotations release asset url", e)
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
            downloadToFile(downloadUrl, tmpZip)
            unzipApiFileToDest(tmpZip.toFile(), targetDir.toFile())
        } finally {
            try {
                Files.deleteIfExists(tmpZip)
            } catch (_: Exception) {
                log.error("Failed to delete temp file $tmpZip")
            }
        }
    }

    private fun downloadToFile(urlStr: String, outPath: Path) {
        val url = URL(urlStr)
        (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
        }.inputStream.use { input ->
            BufferedInputStream(input).use { bis ->
                FileOutputStream(outPath.toFile()).use { fos ->
                    bis.copyTo(fos)
                }
            }
        }
    }

    private fun unzipApiFileToDest(zipFile: File, destDir: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val outFile = destDir.toPath().resolve(entry!!.name).toFile()
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
            }
        }
    }
}
