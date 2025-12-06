package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldEditorConfig
import com.aridclown.intellij.defold.DefoldPathResolver
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

abstract class DefoldProjectAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project.isDefoldProject
    }

    protected inline fun withDefoldProject(
        event: AnActionEvent,
        crossinline action: (Project) -> Unit
    ) {
        val project = event.project ?: return
        if (!project.isDefoldProject) return
        action(project)
    }

    protected inline fun withDefoldConfig(
        project: Project,
        crossinline action: (DefoldEditorConfig) -> Unit
    ) {
        val config = DefoldPathResolver.ensureEditorConfig(project) ?: return
        action(config)
    }
}
