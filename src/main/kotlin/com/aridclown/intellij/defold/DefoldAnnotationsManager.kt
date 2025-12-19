package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldCoroutineService.Companion.launch
import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldVersion
import com.aridclown.intellij.defold.util.NotificationService.notify
import com.aridclown.intellij.defold.util.NotificationService.notifyWarning
import com.aridclown.intellij.defold.util.stdLibraryRootPath
import com.intellij.notification.NotificationAction.createSimpleExpiring
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Comparator

/**
 * Downloads, caches and attaches Defold API annotations to LuaLS.
 * - Determines the target version from the project (fallback to latest)
 * - Downloads zip snapshot for the tag or main when not found
 * - Extracts only the `api/` directory into cache
 * - Creates `.luarc.json` file for LuaLS to automatically discover the API paths
 */
class DefoldAnnotationsManager(
    private val project: Project,
    private val downloader: AnnotationsDownloader = AnnotationsDownloader(),
    private val luarcManager: LuarcConfigurationManager = LuarcConfigurationManager()
) {
    private val logger = Logger.getInstance(DefoldAnnotationsManager::class.java)

    suspend fun ensureAnnotationsAttached() {
        val defoldVersion = project.defoldVersion
        val targetDir = cacheDirForTag(defoldVersion)
        val apiDir = targetDir.resolve("defold_api")
        val needsExtraction = targetDir.needsExtraction()

        if (needsExtraction) {
            val downloaded = downloadAnnotations(defoldVersion, targetDir, "Setting up Defold annotations")
            if (downloaded) {
                refreshAnnotationsRoot(targetDir, apiDir)
            }
        }

        luarcManager.ensureConfiguration(project, apiDir)
    }

    suspend fun reloadAnnotations() {
        val defoldVersion = project.defoldVersion
        val targetDir = cacheDirForTag(defoldVersion)
        val apiDir = targetDir.resolve("defold_api")
        val tempDirParent = targetDir.parent
        val tempDir = when {
            tempDirParent != null -> Files.createTempDirectory(tempDirParent, "${targetDir.fileName}-reload-")
            else -> Files.createTempDirectory("defold-annotations-reload-")
        }

        try {
            val downloaded = downloadAnnotations(defoldVersion, tempDir, "Reloading Defold annotations")
            if (downloaded) {
                replaceAnnotations(targetDir, tempDir)
                refreshAnnotationsRoot(targetDir, apiDir)
            }
        } finally {
            tempDir.deleteRecursivelyIfExists()
        }

        luarcManager.ensureConfiguration(project, apiDir)
    }

    private fun handleAnnotationsFailure(
        project: Project,
        error: Throwable
    ) {
        if (error is UnknownHostException) {
            project.notify(
                title = "Defold annotations failed",
                content =
                buildString {
                    append("Failed to download Defold annotations. ")
                    append("Verify your connection, proxy, and firewall settings before trying again.")
                },
                type = WARNING,
                actions =
                listOf(
                    createSimpleExpiring("Retry") {
                        project.launch(::ensureAnnotationsAttached)
                    }
                )
            )
            return
        }

        logger.warn("Failed to setup Defold annotations", error)
        project.notifyWarning(
            title = "Defold annotations failed",
            content = error.message ?: "Unknown error"
        )
    }

    private fun refreshAnnotationsRoot(
        targetDir: Path,
        apiDir: Path
    ) = LocalFileSystem.getInstance().refreshNioFiles(listOf(targetDir, apiDir))

    private fun cacheDirForTag(tag: String?): Path {
        val actualTag = tag?.takeUnless { it.isBlank() } ?: "latest"
        return stdLibraryRootPath().resolve(actualTag).also(Files::createDirectories)
    }

    private fun Path.needsExtraction(): Boolean = when {
        Files.notExists(this) || !Files.isDirectory(this) -> true
        else -> Files.list(this).use { it.findFirst().isEmpty }
    }

    private suspend fun downloadAnnotations(
        defoldVersion: String?,
        destinationDir: Path,
        progressTitle: String
    ): Boolean = withBackgroundProgress(project, progressTitle, false) {
        runCatching {
            val downloadUrl = downloader.resolveDownloadUrl(defoldVersion)
            downloader.downloadAndExtract(downloadUrl, destinationDir)
        }.onFailure { error ->
            handleAnnotationsFailure(project, error)
        }.isSuccess
    }

    private fun replaceAnnotations(targetDir: Path, newDir: Path) {
        targetDir.deleteRecursivelyIfExists()
        targetDir.parent?.let(Files::createDirectories)
        Files.move(newDir, targetDir, REPLACE_EXISTING)
    }

    private fun Path.deleteRecursivelyIfExists() {
        if (Files.notExists(this)) return
        Files.walk(this).use { stream ->
            stream
                .sorted(Comparator.reverseOrder())
                .forEach { path ->
                    Files.deleteIfExists(path)
                }
        }
    }

    companion object {
        fun getInstance(project: Project): DefoldAnnotationsManager = DefoldAnnotationsManager(project)
    }
}
