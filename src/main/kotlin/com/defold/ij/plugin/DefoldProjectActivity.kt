package com.defold.ij.plugin

import com.intellij.notification.NotificationType
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

        NotificationService.notify(
            project = project,
            title = "Defold project detected",
            content = "Defold project detected$versionText",
            type = NotificationType.INFORMATION,
            actionText = "Install"
        ) { _, notification ->
            // Close the notification for now
            notification.expire()
        }
    }

    private suspend fun registerDefoldScriptFileTypes() = edtWriteAction {
        val fileTypeManager = FileTypeManager.getInstance()
        val luaFileType = fileTypeManager.getFileTypeByExtension("lua")

        if (luaFileType.name != "UNKNOWN") {
            listOf("*.script", "*.gui_script", "*.render_script", "*.editor_script")
                .forEach { pattern -> fileTypeManager.associatePattern(luaFileType, pattern) }
        }
    }
}
