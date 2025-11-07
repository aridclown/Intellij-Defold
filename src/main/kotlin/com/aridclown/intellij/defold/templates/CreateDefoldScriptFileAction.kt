package com.aridclown.intellij.defold.templates

import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.util.DefoldIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

class CreateDefoldScriptFileAction : CreateFileFromTemplateAction(
    "Defold Script File",
    "Create a new Defold script file",
    DefoldScriptTemplate.SCRIPT.icon ?: DefoldIcons.defoldIcon
), DumbAware {

    public override fun buildDialog(
        project: Project,
        directory: PsiDirectory,
        builder: CreateFileFromTemplateDialog.Builder
    ) {
        builder.setTitle("New Defold Script File")
        DefoldScriptTemplate.entries.forEach { template ->
            val icon = template.icon ?: DefoldIcons.defoldIcon
            builder.addKind(template.displayName, icon, template.templateName)
        }
    }

    public override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String): String =
        "Defold Script File"

    override fun update(event: AnActionEvent) {
        super.update(event)
        event.presentation.isEnabledAndVisible = event.project?.isDefoldProject == true
    }
}
