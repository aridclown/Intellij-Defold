package com.aridclown.intellij.defold.templates

import com.aridclown.intellij.defold.DefoldProjectService.Companion.getService
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

class CreateDefoldScriptFileAction : CreateFileFromTemplateAction(
    "Defold Script File",
    "Create a new Defold script file",
    DefoldScriptTemplate.SCRIPT.icon
), DumbAware {

    override fun buildDialog(
        project: Project,
        directory: PsiDirectory,
        builder: CreateFileFromTemplateDialog.Builder
    ) {
        builder.setTitle("New Defold Script File")
        DefoldScriptTemplate.entries.forEach { template ->
            builder.addKind(template.displayName, template.icon, template.templateName)
        }
    }

    override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String): String =
        "Defold Script File"

    override fun update(e: AnActionEvent) {
        super.update(e)
        val project = e.project ?: run {
            e.presentation.isEnabledAndVisible = false
            return
        }

        if (!project.getService().isDefoldProject) {
            e.presentation.isEnabledAndVisible = false
        }
    }
}
