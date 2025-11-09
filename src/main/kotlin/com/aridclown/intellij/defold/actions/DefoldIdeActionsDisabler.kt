package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionManagerEx

object DefoldIdeActionsDisabler {

    private val targetActionIds = listOf(
        "BuildMenu",
        "CompileDirty",
        "MakeModule",
        "Compile",
        "CompileFile",
        "CompileProject",
        "BuildArtifact",
    )

    fun install(actionManager: ActionManagerEx = ActionManagerEx.getInstanceEx()) {
        targetActionIds.forEach { id ->
            val original = actionManager.getAction(id) ?: return@forEach
            if (original is DefoldAwareActionWrapper) return@forEach
            actionManager.replaceAction(id, original.wrap())
        }
    }

    private fun AnAction.wrap(): AnAction = when (this) {
        is ActionGroup -> DefoldAwareGroup(this)
        else -> DefoldAwareAction(this)
    }

    private interface DefoldAwareActionWrapper

    private class DefoldAwareAction(
        private val delegate: AnAction,
    ) : AnAction(), DefoldAwareActionWrapper {

        init {
            templatePresentation.copyFrom(delegate.templatePresentation)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = delegate.actionUpdateThread

        override fun isDumbAware(): Boolean = delegate.isDumbAware

        override fun actionPerformed(event: AnActionEvent) {
            delegate.actionPerformed(event)
        }

        override fun update(event: AnActionEvent) {
            if (event.project?.isDefoldProject == true) {
                event.presentation.isEnabledAndVisible = false
                return
            }

            delegate.update(event)
        }
    }

    private class DefoldAwareGroup(
        private val delegate: ActionGroup,
    ) : ActionGroup(), DefoldAwareActionWrapper {

        init {
            templatePresentation.copyFrom(delegate.templatePresentation)
            isPopup = delegate.isPopup
        }

        override fun getActionUpdateThread(): ActionUpdateThread = delegate.actionUpdateThread

        override fun isDumbAware(): Boolean = delegate.isDumbAware

        override fun actionPerformed(event: AnActionEvent) {
            delegate.actionPerformed(event)
        }

        override fun update(event: AnActionEvent) {
            if (event.project?.isDefoldProject == true) {
                event.presentation.isEnabledAndVisible = false
                return
            }

            delegate.update(event)
        }

        override fun getChildren(event: AnActionEvent?): Array<AnAction> = delegate.getChildren(event)
    }
}
