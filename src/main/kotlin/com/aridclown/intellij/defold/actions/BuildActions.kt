package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldPathResolver
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.debugger.DefoldRunConfigurationUtil
import com.aridclown.intellij.defold.debugger.MobDebugRunConfiguration
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages

abstract class AbstractBuildAction(
    private val buildCommands: List<String>,
) : DumbAwareAction() {

    override fun getActionUpdateThread() = BGT

    override fun update(event: AnActionEvent): Unit = with(event) {
        if (project.isDefoldProject) {
            presentation.isEnabledAndVisible = true
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        if (!project.isDefoldProject) return

        if (DefoldPathResolver.ensureEditorConfig(project) == null) {
            return
        }

        val settings = DefoldRunConfigurationUtil.getOrCreate(project)
        val runConfiguration = settings.configuration as? MobDebugRunConfiguration ?: return

        runConfiguration.runtimeBuildCommands = buildCommands
        runConfiguration.runtimeEnableDebugScript = true

        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }
}

class BuildProjectAction : AbstractBuildAction(
    buildCommands = listOf("build"),
)

class CleanBuildProjectAction : AbstractBuildAction(
    buildCommands = listOf("distclean", "build"),
) {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        if (!project.isDefoldProject) return

        val confirmed = Messages.showOkCancelDialog(
            project,
            "Are you sure you want to perform a clean build?",
            "Perform Clean Build?",
            "Clean Build",
            Messages.getCancelButton(),
            Messages.getQuestionIcon()
        ) == Messages.OK

        if (!confirmed) {
            return
        }

        super.actionPerformed(event)
    }
}
