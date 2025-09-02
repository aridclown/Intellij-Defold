package com.defold.ij.plugin

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class DefoldProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        println("DefoldProjectActivity executing...")
        val service = DefoldProjectService.getInstance(project)
        val detected = service.detect()

        if (detected) {
            val version = service.getDefoldVersion()
            showDefoldDetectedNotification(project, version)

            // Register Defold script file patterns with Lua file type
            registerDefoldScriptFileTypes()

            // Ensure Defold API annotations are downloaded, cached and configured with SumnekoLua
            try {
                DefoldAnnotationsManager.ensureAnnotationsAttached(project, version)
            } catch (t: Throwable) {
                println("Defold annotations setup failed: ${t.message}")
            }
        } else {
            println("No Defold project detected.")
        }

        println(" DefoldProjectActivity executed. Defold detected=$detected")
    }

    private fun showDefoldDetectedNotification(project: Project, version: String?) {
        val versionText = version?.let { " (version $it)" } ?: ""
        val content = "Defold project detected$versionText"

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Defold")
            .createNotification(
                "Defold project detected",
                content,
                NotificationType.INFORMATION
            )

        notification.addAction(object : NotificationAction("Install") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                // Close the notification for now
                notification.expire()
            }
        })

        notification.notify(project)
    }

    private suspend fun registerDefoldScriptFileTypes() {
        edtWriteAction {
            val fileTypeManager = FileTypeManager.getInstance()
            val luaFileType = fileTypeManager.getFileTypeByExtension("lua")

            if (luaFileType.name != "UNKNOWN") {
                // Associate the patterns with the Lua file type
                fileTypeManager.associatePattern(luaFileType, "*.script")
                fileTypeManager.associatePattern(luaFileType, "*.gui_script")
            } else {
                println("Could not find Lua file type to associate Defold scripts")
            }
        }
    }
}
