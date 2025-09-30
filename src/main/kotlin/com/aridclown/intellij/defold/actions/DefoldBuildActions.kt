package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldEditorConfig
import com.aridclown.intellij.defold.DefoldProjectRunner
import com.aridclown.intellij.defold.DefoldProjectService.Companion.createConsole
import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldProjectService
import com.aridclown.intellij.defold.DefoldProjectService.Companion.findActiveConsole
import com.aridclown.intellij.defold.ui.NotificationService.notifyError
import com.intellij.execution.configuration.EnvironmentVariablesData.DEFAULT
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue

abstract class AbstractDefoldBuildAction(
    private val buildCommands: List<String>,
) : DumbAwareAction() {

    override fun getActionUpdateThread() = BGT

    override fun update(e: AnActionEvent) {
        e.project?.defoldProjectService()?.isDefoldProject?.ifTrue {
            e.presentation.isEnabledAndVisible = true
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.defoldProjectService().isDefoldProject.ifFalse { return }

        val config = DefoldEditorConfig.loadEditorConfig()
        if (config == null) {
            project.notifyError("Defold", "Defold editor installation not found.")
            return
        }

        val console = project.findActiveConsole() ?: project.createConsole()

        DefoldProjectRunner.run(
            project = project,
            config = config,
            console = console,
            enableDebugScript = false,
            envData = DEFAULT,
            buildCommands = buildCommands
        )
    }
}

class DefoldBuildProjectAction : AbstractDefoldBuildAction(
    buildCommands = listOf("build"),
)

class DefoldCleanBuildProjectAction : AbstractDefoldBuildAction(
    buildCommands = listOf("distclean", "build"),
)
