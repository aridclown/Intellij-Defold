package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup

class DefoldProjectMenuGroup : DefaultActionGroup() {
    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = e.project?.isDefoldProject == true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = BGT
}
