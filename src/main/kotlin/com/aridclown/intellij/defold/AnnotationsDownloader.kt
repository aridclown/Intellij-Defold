package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.util.SimpleHttpClient
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
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
            val response = SimpleHttpClient.get(downloadUrl, Duration.ofSeconds(10))
            val body = response.body

            if (response.code == 404) {
                throw ReleaseNotFoundException(notFoundMessage(defoldVersion))
            }

            if (response.code !in 200..299) {
                throw Exception("Unexpected response ${response.code} received from GitHub releases API")
            }

            val json = body ?: throw Exception("Empty response received from GitHub releases API")
            val obj = JsonParser.parseString(json).asJsonObject
            val assets = obj.getAsJsonArray("assets")

            if (assets.size() == 0) throw Exception("No assets found in release")

            assets[0].asJsonObject
                .get("browser_download_url")
                .asString
        } catch (e: UnknownHostException) {
            throw e
        } catch (e: InterruptedIOException) {
            throw Exception("Could not resolve Defold annotations due to timeout", e)
        } catch (e: ReleaseNotFoundException) {
            logger.warn(e.message)
            throw e
        } catch (e: Exception) {
            logger.error("Failed to fetch Defold annotations release asset url", e)
            throw Exception("Could not resolve Defold annotations download URL", e)
        }
    }

    private fun notFoundMessage(defoldVersion: String?): String {
        val desiredVersion = defoldVersion?.takeUnless { it.isBlank() } ?: "latest"
        return "Defold annotations release '$desiredVersion' was not found on GitHub."
    }

    fun downloadAndExtract(
        downloadUrl: String,
        targetDir: Path
    ) {
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

    private fun unzipToDestination(
        zipFile: Path,
        destDir: Path
    ) {
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

private class ReleaseNotFoundException(message: String) : Exception(message)
