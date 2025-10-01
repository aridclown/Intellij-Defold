package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldEditorConfig
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.debugger.DefoldRunConfigurationUtil
import com.aridclown.intellij.defold.debugger.MobDebugRunConfiguration
import com.aridclown.intellij.defold.ui.NotificationService.notifyError
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue

abstract class AbstractDefoldBuildAction(
    private val buildCommands: List<String>,
) : DumbAwareAction() {

    override fun getActionUpdateThread() = BGT

    override fun update(event: AnActionEvent): Unit = with(event) {
        project.isDefoldProject.ifTrue {
            presentation.isEnabledAndVisible = true
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        project.isDefoldProject.ifFalse { return }

        if (DefoldEditorConfig.loadEditorConfig() == null) {
            project.notifyError("Defold", "Defold editor installation not found.")
            return
        }

        val settings = DefoldRunConfigurationUtil.getOrCreate(project)
        val runConfiguration = settings.configuration as? MobDebugRunConfiguration ?: return

        runConfiguration.runtimeBuildCommands = buildCommands
        runConfiguration.runtimeEnableDebugScript = true

        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }
}

class DefoldBuildProjectAction : AbstractDefoldBuildAction(
    buildCommands = listOf("build"),
)

class DefoldCleanBuildProjectAction : AbstractDefoldBuildAction(
    buildCommands = listOf("distclean", "build"),
) {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        project.isDefoldProject.ifFalse { return }

        val confirmed = Messages.showOkCancelDialog(
            project,
            "Are you sure you want to perform a clean build?",
            "Perform Clean Build?",
            "Clean Build",
            "Cancel",
            Messages.getQuestionIcon()
        ) == Messages.OK

        if (!confirmed) {
            return
        }

        super.actionPerformed(event)
    }
}
