package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.hotreload.DefoldHotReloadService.Companion.hotReloadProjectService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager

class DefoldHotReloadAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = BGT

    override fun actionPerformed(event: AnActionEvent) {
        val hotReloadService = event.project?.hotReloadProjectService() ?: return

        runWriteAction {
            // Save all before hot reloading
            FileDocumentManager.getInstance().saveAllDocuments()
            getApplication().executeOnPooledThread { hotReloadService.performHotReload() }
        }
    }

    override fun update(event: AnActionEvent) = with(event) {
        presentation.isEnabledAndVisible = project.isDefoldProject
    }
}