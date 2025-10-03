package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.hotreload.DefoldHotReloadService.Companion.hotReloadProjectService
import com.aridclown.intellij.defold.process.DefoldCoroutineService.Companion.launch
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager

class DefoldHotReloadAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = BGT

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val hotReloadService = project.hotReloadProjectService()

        project.launch {
            // Save all before hot reloading
            edtWriteAction(FileDocumentManager.getInstance()::saveAllDocuments)
            hotReloadService.performHotReload()
        }
    }

    override fun update(event: AnActionEvent) = with(event) {
        val hotReloadService = project?.hotReloadProjectService()
        presentation.isEnabled = project.isDefoldProject && hotReloadService?.hasReachableEngine() == true
    }
}
