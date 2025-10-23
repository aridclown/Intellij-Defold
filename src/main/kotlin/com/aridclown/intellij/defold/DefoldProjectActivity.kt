package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldAnnotationsManager.ensureAnnotationsAttached
import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldVersion
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.actions.BuildActionManager
import com.aridclown.intellij.defold.actions.NewGroupActionManager
import com.aridclown.intellij.defold.util.NotificationService.notify
import com.aridclown.intellij.defold.util.trySilently
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class DefoldProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (project.isDefoldProject) {
            println("Defold project detected.")

            // Register Defold-specific "New" actions into the "New" action group
            NewGroupActionManager.register()
            BuildActionManager.unregister()

            val version = project.defoldVersion
            showDefoldDetectedNotification(project, version)

            // Register Defold script file patterns with Lua file types
            registerDefoldScriptFileTypes()

            // Ensure the project window icon exists for Defold projects
            ensureProjectIcon(project)

            // Ensure project modules are configured correctly
            configureProjectModules(project)

            // Ensure Defold API annotations are downloaded, cached and configured with LuaLS
            ensureAnnotationsAttached(project, version)
        } else {
            println("No Defold project detected.")
        }
    }

    private fun showDefoldDetectedNotification(project: Project, version: String?) {
        val versionText = version?.let { "(version $it)" } ?: ""

        val title = "Defold project detected"
        project.notify(title, content = "$title $versionText", INFORMATION)
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

        trySilently {
            val resource = javaClass.classLoader.getResourceAsStream("icons/icon.png") ?: return@trySilently
            Files.createDirectories(ideaDir)
            resource.use { Files.copy(it, iconPng, REPLACE_EXISTING) }
        }
    }

    private suspend fun configureProjectModules(project: Project) = edtWriteAction {
        val basePath = project.basePath ?: return@edtWriteAction
        val baseDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath) ?: return@edtWriteAction

        project.ensureDefoldModule()
            ?.configureDefoldRoots(baseDir) ?: return@edtWriteAction
    }
}

private val DEFOLD_DEFAULT_EXCLUDES = listOf(".git", ".idea", "build", ".internal", "debugger")

fun Project.ensureDefoldModule(): Module? {
    val basePath = this.basePath ?: return null
    val moduleManager = ModuleManager.getInstance(this)

    return moduleManager.modules.firstOrNull() ?: moduleManager.newModule(
        file = Path.of(basePath, DIRECTORY_STORE_FOLDER, "$name.iml"),
        moduleTypeId = EmptyModuleType.getInstance().id
    )
}

/**
 * Idempotently:
 *  - adds the project baseDir as a Content Root (if missing)
 *  - marks baseDir as a source folder (non-test) if not already
 *  - adds reasonable excludes
 */
private fun Module.configureDefoldRoots(baseDir: VirtualFile) =
    ModuleRootModificationUtil.updateModel(this) { model ->
        val entry = model.contentEntries.find { it.file == baseDir } ?: model.addContentEntry(baseDir)

        val hasSourceAtRoot = entry.sourceFolders.any { it.file == baseDir && !it.isTestSource }
        if (!hasSourceAtRoot) {
            entry.addSourceFolder(baseDir, /* isTestSource = */ false)
        }

        DEFOLD_DEFAULT_EXCLUDES.forEach { name ->
            val child = baseDir.findChild(name)
            when {
                child != null && entry.excludeFolderUrls.none { it == child.url } -> entry.addExcludeFolder(child)
                !entry.excludePatterns.contains(name) -> entry.addExcludePattern(name)
            }
        }
    }