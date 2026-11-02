package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldCoroutineService.Companion.launch
import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldProjectService
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Comparator

/**
 * Loads, caches and attaches Defold API annotations to LuaLS.
 * - Determines the target version from the project (fallback to latest)
 * - Uses bundled editor annotations when available, otherwise downloads a matching release
 * - Stores annotations under `defold_api/` in the cache
 * - Creates `.luarc.json` file for LuaLS to automatically discover the API paths
 */
class DefoldAnnotationsManager(
    private val project: Project,
    private val downloader: AnnotationsDownloader = AnnotationsDownloader(),
    private val luarcManager: LuarcConfigurationManager = LuarcConfigurationManager(),
    private val bundledAnnotations: BundledAnnotations = BundledAnnotations(),
    private val editorJar: () -> Path? = { project.defoldProjectService().editorConfig?.editorJar?.let(Path::of) },
    private val moveCache: (Path, Path) -> Unit = { source, destination ->
        Files.move(source, destination)
        Unit
    }
) {
    private val logger = Logger.getInstance(DefoldAnnotationsManager::class.java)

    suspend fun ensureAnnotationsAttached() = cacheMutex.withLock {
        val defoldVersion = project.defoldVersion
        val targetDir = cacheDirForTag(defoldVersion)
        val apiDir = targetDir.resolve("defold_api")
        repairLegacyCache(targetDir, apiDir)

        val jar = editorJar()
        val migrate = apiDir.hasContent() && jar != null &&
            !Files.exists(targetDir.resolve("bundled"))
        if (!apiDir.hasContent() || migrate) {
            updateAnnotations(defoldVersion, targetDir, "Setting up Defold annotations", migrate)
        }
        luarcManager.ensureConfiguration(project, apiDir)
    }

    suspend fun reloadAnnotations() = cacheMutex.withLock {
        val version = project.defoldVersion
        val target = cacheDirForTag(version)
        updateAnnotations(version, target, "Reloading Defold annotations")
        luarcManager.ensureConfiguration(project, target.resolve("defold_api"))
    }

    private suspend fun updateAnnotations(
        version: String?,
        target: Path,
        title: String,
        bundledOnly: Boolean = false
    ) = withBackgroundProgress(project, title, false) {
        val staging = Files.createTempDirectory(target.parent, "annotations-")
        try {
            val jar = editorJar()
            val bundled = try {
                jar != null && bundledAnnotations.extract(jar, staging)
            } catch (error: IOException) {
                logger.warn("Cannot read bundled annotations; using GitHub fallback", error)
                staging.deleteRecursivelyIfExists()
                Files.createDirectories(staging)
                false
            }
            if (!bundled) {
                if (bundledOnly) return@withBackgroundProgress
                downloader.downloadAndExtract(downloader.resolveDownloadUrl(version), staging)
            }
            require(staging.resolve("defold_api").hasContent()) { "No Defold annotations were extracted" }
            if (bundled) Files.writeString(staging.resolve("bundled"), "1")
            replaceAnnotations(target, staging)
            refreshAnnotationsRoot(target, target.resolve("defold_api"))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleAnnotationsFailure(project, error)
        } finally {
            staging.deleteRecursivelyIfExists()
        }
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

    private fun repairLegacyCache(
        targetDir: Path,
        apiDir: Path
    ) {
        val legacyRoot = targetDir.resolve("tmp")
        val legacyApiDir = legacyRoot.resolve("defold_api")

        if (!apiDir.hasContent() && legacyApiDir.hasContent()) {
            apiDir.deleteRecursivelyIfExists()
            Files.move(legacyApiDir, apiDir, REPLACE_EXISTING)
        }

        if (apiDir.hasContent()) {
            legacyRoot.deleteRecursivelyIfExists()
        }
    }

    private fun Path.hasContent(): Boolean = Files.isDirectory(this) &&
        Files.list(this).use { it.findFirst().isPresent }

    private fun replaceAnnotations(targetDir: Path, newDir: Path) {
        val backup = Files.createTempDirectory(targetDir.parent, "annotations-backup-")
        Files.delete(backup)
        moveCache(targetDir, backup)
        try {
            moveCache(newDir, targetDir)
        } catch (error: Exception) {
            moveCache(backup, targetDir)
            throw error
        }
        backup.deleteRecursivelyIfExists()
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
        private val cacheMutex = Mutex()

        fun getInstance(project: Project): DefoldAnnotationsManager = DefoldAnnotationsManager(project)
    }
}
