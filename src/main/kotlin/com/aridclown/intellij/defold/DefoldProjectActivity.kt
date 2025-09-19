package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldAnnotationsManager.ensureAnnotationsAttached
import com.aridclown.intellij.defold.DefoldProjectService.Companion.getService
import com.aridclown.intellij.defold.ui.NotificationService
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class DefoldProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val projectService = project.getService()

        if (projectService.isDefoldProject) {
            println("Defold project detected.")

            val version = projectService.defoldVersion
            showDefoldDetectedNotification(project, version)

            // Register Defold script file patterns with Lua file type
            registerDefoldScriptFileTypes()

            // Ensure project window icon exists for Defold projects
            ensureProjectIcon(project)

            // Ensure project root is marked as Sources Root
            ensureRootIsSourcesRoot(project)

            // Ensure Defold API annotations are downloaded, cached and configured with SumnekoLua
            ensureAnnotationsAttached(project, version)
        } else {
            println("No Defold project detected.")
        }
    }

    private fun showDefoldDetectedNotification(project: Project, version: String?) {
        val versionText = version?.let { " (version $it)" } ?: ""

        NotificationService.notify(
            project = project,
            title = "Defold project detected",
            content = "Defold project detected$versionText",
            type = NotificationType.INFORMATION,
            actionText = "Install"
        ) { _, notification ->
            notification.expire() // Close the notification for now
        }
    }

    private suspend fun registerDefoldScriptFileTypes() = edtWriteAction {
        val fileTypeManager = FileTypeManager.getInstance()

        // Map of file type extensions to their associated patterns
        val fileTypeAssociations = mapOf(
            "lua" to DefoldScriptType.entries.map { "*.${it.extension}" },
            "glsl" to listOf("*.fp", "*.vp"),
            "ini" to listOf("*.project"),
            "json" to listOf("*.buffer"),
            "yaml" to listOf("*.appmanifest", "ext.manifest", "*.script_api")
        )

        fun FileType.applyPatterns(patterns: List<String>) {
            patterns.forEach { pattern -> fileTypeManager.associatePattern(this, pattern) }
        }

        fileTypeAssociations.forEach { (extension, patterns) ->
            fileTypeManager.getFileTypeByExtension(extension)
                .takeIf { it.name != "UNKNOWN" }
                ?.applyPatterns(patterns)
        }
    }

    private fun ensureProjectIcon(project: Project) {
        val basePath = project.basePath ?: return
        val ideaDir = Path.of(basePath, ".idea")

        if (!Files.isDirectory(ideaDir)) {
            setupIdeaDirListener(project, basePath) // .idea directory doesn't exist yet
            return
        }

        // .idea exists, add the icon
        createIconIfNeeded(basePath)
    }

    private fun setupIdeaDirListener(project: Project, basePath: String) {
        val connection = project.messageBus.connect()

        connection.subscribe(topic = VFS_CHANGES, handler = object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                events.forEach { event ->
                    if (event is VFileCreateEvent &&
                        event.file?.name == ".idea" &&
                        event.file?.parent?.path == basePath
                    ) {

                        // .idea directory was created, add the icon
                        createIconIfNeeded(basePath)

                        // Disconnect listener after handling
                        connection.disconnect()
                    }
                }
            }
        })
    }

    private fun createIconIfNeeded(basePath: String) {
        val ideaDir = Path.of(basePath, ".idea")
        val iconPng = ideaDir.resolve("icon.png")
        if (Files.exists(iconPng)) return

        try {
            val resource = javaClass.classLoader.getResourceAsStream("icons/icon.png") ?: return
            Files.createDirectories(ideaDir)
            resource.use { Files.copy(it, iconPng, REPLACE_EXISTING) }
        } catch (_: Exception) {
            // Best-effort; ignore failures
        }
    }

    private suspend fun ensureRootIsSourcesRoot(project: Project) = edtWriteAction {
        val basePath = project.basePath ?: return@edtWriteAction
        val baseDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath) ?: return@edtWriteAction

        ModuleManager.getInstance(project).modules.forEach { module ->
            val model = ModuleRootManager.getInstance(module).modifiableModel
            var changed = false

            try {
                val contentEntry = model.contentEntries.find { it.file == baseDir }
                    ?: model.addContentEntry(baseDir).also { changed = true }

                val hasSourceAtRoot = contentEntry.sourceFolders.any { it.file == baseDir && !it.isTestSource }
                if (!hasSourceAtRoot) {
                    contentEntry.addSourceFolder(baseDir, /* isTestSource = */ false)
                    changed = true
                }
            } finally {
                if (changed) model.commit() else model.dispose()
            }
        }
    }

}
