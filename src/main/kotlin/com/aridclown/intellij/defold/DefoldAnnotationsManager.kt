package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldCoroutineService.Companion.launch
import com.aridclown.intellij.defold.util.NotificationService.notify
import com.aridclown.intellij.defold.util.NotificationService.notifyInfo
import com.aridclown.intellij.defold.util.NotificationService.notifyWarning
import com.aridclown.intellij.defold.util.stdLibraryRootPath
import com.intellij.notification.NotificationAction.createSimpleExpiring
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Downloads, caches and attaches Defold API annotations to LuaLS.
 * - Determines the target version from the project (fallback to latest)
 * - Downloads zip snapshot for the tag or main when not found
 * - Extracts only the `api/` directory into cache
 * - Creates `.luarc.json` file for LuaLS to automatically discover the API paths
 */
@Service(PROJECT)
class DefoldAnnotationsManager(
    private val downloader: AnnotationsDownloader = AnnotationsDownloader(),
    private val luarcManager: LuarcConfigurationManager = LuarcConfigurationManager()
) {
    private val logger = Logger.getInstance(DefoldAnnotationsManager::class.java)

    suspend fun ensureAnnotationsAttached(project: Project, defoldVersion: String?) {
        val targetDir = cacheDirForTag(defoldVersion)
        val apiDir = targetDir.resolve("defold_api")
        val needsExtraction = targetDir.needsExtraction()

        if (needsExtraction) {
            withBackgroundProgress(project, "Setting up Defold annotations", false) {
                runCatching {
                    val downloadUrl = downloader.resolveDownloadUrl(defoldVersion)
                    downloader.downloadAndExtract(downloadUrl, targetDir)
                    refreshAnnotationsRoot(targetDir, apiDir)
                }.onSuccess {
                    project.notifyInfo(
                        title = "Defold annotations ready",
                        content = "Configured LuaLS for Defold API ${defoldVersion ?: "latest"} via .luarc.json"
                    )
                }.onFailure { error ->
                    handleAnnotationsFailure(project, defoldVersion, error)
                }
            }
        }

        luarcManager.ensureConfiguration(project, apiDir)
    }

    private fun handleAnnotationsFailure(project: Project, defoldVersion: String?, error: Throwable) {
        if (error is UnknownHostException) {
            project.notify(
                title = "Defold annotations failed",
                content = "Failed to download Defold annotations. Verify your connection, proxy, and firewall settings before trying again.",
                type = WARNING,
                actions = listOf(createSimpleExpiring("Retry") {
                    project.launch {
                        project.service<DefoldAnnotationsManager>()
                            .ensureAnnotationsAttached(project, defoldVersion)
                    }
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

    private fun cacheDirForTag(tag: String?): Path {
        val actualTag = tag?.takeUnless { it.isBlank() } ?: "latest"
        return stdLibraryRootPath().resolve(actualTag).also(Files::createDirectories)
    }

    private fun Path.needsExtraction(): Boolean = when {
        Files.notExists(this) || !Files.isDirectory(this) -> true
        else -> Files.list(this).use { it.findFirst().isEmpty }
    }

    companion object {
        fun getInstance(project: Project): DefoldAnnotationsManager = project.service()
    }
}
