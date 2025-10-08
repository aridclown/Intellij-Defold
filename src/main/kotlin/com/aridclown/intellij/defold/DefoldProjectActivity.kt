package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldAnnotationsManager.ensureAnnotationsAttached
import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldVersion
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.actions.DefoldBuildActionManager
import com.aridclown.intellij.defold.actions.DefoldNewGroupActionManager
import com.aridclown.intellij.defold.ui.NotificationService.notify
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
import com.tang.intellij.lua.lang.LuaLanguageLevel.LUA51
import com.tang.intellij.lua.lang.LuaLanguageLevel.LUA53
import com.tang.intellij.lua.project.LuaSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class DefoldProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (project.isDefoldProject) {
            println("Defold project detected.")

            // Register Defold-specific "New" actions into the "New" action group
            DefoldNewGroupActionManager.register()
            DefoldBuildActionManager.unregister()

            val version = project.defoldVersion
            showDefoldDetectedNotification(project, version)

            // Ensure Lua language level is Defold-compatible
            configureLuaLanguageLevel()

            // Register Defold script file patterns with Lua file types
            registerDefoldScriptFileTypes()

            // Ensure the project window icon exists for Defold projects
            ensureProjectIcon(project)

            // Ensure project modules are configured correctly
            configureProjectModules(project)

            // Ensure Defold API annotations are downloaded, cached and configured with SumnekoLua
            ensureAnnotationsAttached(project, version)
        } else {
            println("No Defold project detected.")
        }
    }

    /**
     * Ensure Lua language level is Defold-compatible.
     * Defold uses LuaJIT, which implements Lua 5.1 with a scattering of 5.2/5.3 extras
     *
     * @see <a href="https://defold.com/manuals/lua/#lua-versions">Defold Lua versions</a>
     */
    private fun configureLuaLanguageLevel() {
        val settings = LuaSettings.instance
        // Only if it's still the default, so we don't break custom projects.
        if (settings.languageLevel == LUA53) settings.languageLevel = LUA51
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

        try {
            val resource = javaClass.classLoader.getResourceAsStream("icons/icon.png") ?: return
            Files.createDirectories(ideaDir)
            resource.use { Files.copy(it, iconPng, REPLACE_EXISTING) }
        } catch (_: Exception) {
            // Best-effort; ignore failures
        }
    }

    private suspend fun configureProjectModules(project: Project) = edtWriteAction {
        val basePath = project.basePath ?: return@edtWriteAction
        val baseDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath) ?: return@edtWriteAction

        project.ensureDefoldModule()
            ?.configureDefoldRoots(baseDir) ?: return@edtWriteAction
    }
}

private val DEFOLD_DEFAULT_EXCLUDES = listOf(".git", ".idea", ".gradle", "build", ".internal", "out")

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
            if (child != null && entry.excludeFolderUrls.none { it == child.url }) {
                entry.addExcludeFolder(child)
            } else if (!entry.excludePatterns.contains(name)) {
                entry.addExcludePattern(name)
            }
        }
    }