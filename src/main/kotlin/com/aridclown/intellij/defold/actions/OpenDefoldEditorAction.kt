package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldEditorLauncher
import com.aridclown.intellij.defold.DefoldPathResolver
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.DefoldProjectService.Companion.rootProjectFolder
import com.aridclown.intellij.defold.util.NotificationService.notifyError
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class OpenDefoldEditorAction : DumbAwareAction() {

    override fun getActionUpdateThread() = BGT

    override fun update(event: AnActionEvent): Unit = with(event) {
        presentation.isEnabledAndVisible = project.isDefoldProject
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        if (!project.isDefoldProject) {
            return
        }

        if (DefoldPathResolver.ensureEditorConfig(project) == null) {
            return
        }

        val projectFolder = project.rootProjectFolder
        if (projectFolder == null) {
            project.notifyError(
                title = "Defold",
                content = "No Defold project detected in current workspace."
            )
            return
        }

        DefoldEditorLauncher(project)
            .openDefoldEditor(projectFolder.path)
    }
}
