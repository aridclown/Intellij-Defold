package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.util.SimpleHttpClient
import com.intellij.openapi.diagnostic.Logger
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.zip.ZipInputStream

private const val DEFOLD_ANNOTATIONS_RESOURCE = "https://api.github.com/repos/astrochili/defold-annotations/releases"

class AnnotationsDownloader {
    private val logger = Logger.getInstance(AnnotationsDownloader::class.java)

    fun resolveDownloadUrl(defoldVersion: String?): String {
        val downloadUrl = when {
            defoldVersion.isNullOrBlank() -> "$DEFOLD_ANNOTATIONS_RESOURCE/latest"
            else -> "$DEFOLD_ANNOTATIONS_RESOURCE/tags/$defoldVersion"
        }

        return try {
            val json = SimpleHttpClient.get(downloadUrl, Duration.ofSeconds(10)).body
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

    fun downloadAndExtract(downloadUrl: String, targetDir: Path) {
        val tmpZip = Files.createTempFile("defold-annotations-", ".zip")
        try {
            SimpleHttpClient.downloadToPath(downloadUrl, tmpZip)
            unzipToDestination(tmpZip, targetDir)
        } finally {
            try {
                Files.deleteIfExists(tmpZip)
            } catch (_: Exception) {
                logger.error("Failed to delete temp file $tmpZip")
            }
        }
    }

    private fun unzipToDestination(zipFile: Path, destDir: Path) {
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
}
