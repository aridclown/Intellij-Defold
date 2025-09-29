package com.aridclown.intellij.defold.action

import com.aridclown.intellij.defold.DefoldProjectService
import com.aridclown.intellij.defold.hotreload.DefoldHotReloadService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager

/**
 * Action to trigger hot reload of modified Defold resources.
 *
 * Mirrors the Defold editor's "File > Hot Reload" functionality with Cmd/Ctrl+R shortcut.
 * Only enabled when a Defold project is detected.
 */
class DefoldHotReloadAction : AnAction(
    "Hot Reload",
    "Reload modified resources in running Defold game",
    AllIcons.Actions.Refresh
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        runWriteAction {
            FileDocumentManager.getInstance().saveAllDocuments()

            ApplicationManager.getApplication().executeOnPooledThread {
                project.service<DefoldHotReloadService>().performHotReload()
            }
        }

    }

    override fun update(e: AnActionEvent) {
        val isDefoldProject = e.project
            ?.service<DefoldProjectService>()?.isDefoldProject == true
        e.presentation.isEnabledAndVisible = isDefoldProject
    }
}