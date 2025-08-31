package com.defold.ij.plugin

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException

@Service(Service.Level.PROJECT)
class DefoldProjectService(private val project: Project) {

    private val log = Logger.getInstance(DefoldProjectService::class.java)

    fun detect(): Boolean {
        val detected = findDefoldMarkerFile() != null
        log.info("Defold detection: ${if (detected) "found" else "not found"}")
        return detected
    }

    fun getDefoldVersion(): String? = findEditorSettingsFile()?.let { editorSettingsFile ->
        return try {
            val content = String(editorSettingsFile.contentsToByteArray())
            extractVersionFromEditorSettings(content)?.let { return it }
        } catch (e: IOException) {
            log.warn("Failed to read .editor_settings file", e)
            null
        }
    }

    fun createNotification(
        title: String,
        body: String,
        project: Project,
        type: NotificationType = NotificationType.IDE_UPDATE
    ) {
        Notifications.Bus.notify(Notification("Defold", title, body, type), project)
    }

    private fun extractVersionFromEditorSettings(content: String): String? {
        // Look for paths containing defold-lua-X.X.X pattern in .editor_settings
        val versionRegex = Regex("""defold-lua-(\d+\.\d+\.\d+)""")
        return versionRegex.find(content)?.groupValues?.get(1)
    }

    private fun findEditorSettingsFile(): VirtualFile? {
        val roots = ProjectRootManager.getInstance(project).contentRoots
        for (root in roots) {
            root.findChild(".editor_settings")?.let { return it }
        }
        return null
    }

    private fun findDefoldMarkerFile(): VirtualFile? {
        val roots = ProjectRootManager.getInstance(project).contentRoots
        for (root in roots) {
            root.findChild("game.project")?.let { return it }
            VfsUtil.findRelativeFile(root, "app", "game.project")?.let { return it }
        }
        return null
    }

    companion object {
        fun getInstance(project: Project) = project.service<DefoldProjectService>()
    }
}
