package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.debugger.DefoldRunConfigurationUtil
import com.aridclown.intellij.defold.debugger.MobDebugRunConfiguration
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

abstract class AbstractBuildAction(
    private val buildCommands: List<String>
) : DefoldProjectAction() {

    final override fun actionPerformed(event: AnActionEvent) = withDefoldProject(event, ::performBuild)

    protected open fun performBuild(project: Project) {
        withDefoldConfig(project) {
            val settings = DefoldRunConfigurationUtil.getOrCreate(project)
            val runConfiguration = settings.configuration as? MobDebugRunConfiguration ?: return@withDefoldConfig

            runConfiguration.runtimeBuildCommands = buildCommands
            runConfiguration.runtimeEnableDebugScript = true

            ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
        }
    }
}

class BuildProjectAction : AbstractBuildAction(buildCommands = listOf("build"))

class CleanBuildProjectAction : AbstractBuildAction(buildCommands = listOf("distclean", "build")) {
    override fun performBuild(project: Project) {
        val confirmed = Messages.showOkCancelDialog(
            project,
            "Are you sure you want to perform a clean build?",
            "Perform Clean Build?",
            "Clean Build",
            Messages.getCancelButton(),
            Messages.getQuestionIcon()
        ) == Messages.OK

        if (!confirmed) return

        super.performBuild(project)
    }
}
