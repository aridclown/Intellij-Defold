package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldProjectService.Companion.getService
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class DefoldProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        println("DefoldProjectActivity executing...")
        val projectService = project.getService()

        if (projectService.detect()) {
            println("Defold project detected.")

            val version = projectService.getDefoldVersion()
            showDefoldDetectedNotification(project, version)

            // Register Defold script file patterns with Lua file type
            registerDefoldScriptFileTypes()

            // Ensure Defold API annotations are downloaded, cached and configured with SumnekoLua
            DefoldAnnotationsManager.ensureAnnotationsAttached(project, version)
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
            // Close the notification for now
            notification.expire()
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
}
