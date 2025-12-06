package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldEditorLauncher
import com.aridclown.intellij.defold.DefoldProjectService.Companion.rootProjectFolder
import com.aridclown.intellij.defold.util.NotificationService.notifyError
import com.intellij.openapi.actionSystem.AnActionEvent

class OpenDefoldEditorAction : DefoldProjectAction() {
    override fun actionPerformed(event: AnActionEvent) = withDefoldProject(event) { project ->
        withDefoldConfig(project) {
            val projectFolder = project.rootProjectFolder
            if (projectFolder == null) {
                project.notifyError(
                    title = "Defold",
                    content = "No Defold project detected in current workspace."
                )
                return@withDefoldConfig
            }

            DefoldEditorLauncher(project)
                .openDefoldEditor(projectFolder.path)
        }
    }
}
