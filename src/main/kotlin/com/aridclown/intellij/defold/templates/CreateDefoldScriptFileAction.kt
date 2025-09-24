package com.aridclown.intellij.defold.templates

import com.aridclown.intellij.defold.DefoldProjectService.Companion.getService
import com.aridclown.intellij.defold.ui.DefoldIcons
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

class CreateDefoldScriptFileAction : CreateFileFromTemplateAction(
    "Defold Script File",
    "Create a new Defold script file",
    DefoldScriptTemplate.SCRIPT.icon ?: DefoldIcons.defoldIcon ?: AllIcons.FileTypes.Any_type
), DumbAware {

    override fun buildDialog(
        project: Project,
        directory: PsiDirectory,
        builder: CreateFileFromTemplateDialog.Builder
    ) {
        builder.setTitle("New Defold Script File")
        DefoldScriptTemplate.entries.forEach { template ->
            val icon = template.icon ?: DefoldIcons.defoldIcon ?: AllIcons.FileTypes.Any_type
            builder.addKind(template.displayName, icon, template.templateName)
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
