package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldEditorConfig
import com.aridclown.intellij.defold.DefoldProjectRunner
import com.aridclown.intellij.defold.DefoldProjectService.Companion.createConsole
import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldProjectService
import com.aridclown.intellij.defold.DefoldProjectService.Companion.findActiveConsole
import com.aridclown.intellij.defold.ui.DefoldLogHyperlinkFilter
import com.aridclown.intellij.defold.ui.NotificationService.notifyError
import com.intellij.execution.configuration.EnvironmentVariablesData.DEFAULT
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import javax.swing.Icon

abstract class AbstractDefoldBuildAction(
    text: String,
    description: String,
    val icon: Icon,
    private val buildCommands: List<String>,
) : DumbAwareAction(text, description, icon) {

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
            buildCommands = buildCommands,
            onEngineStarted = {}
        )
    }
}

class DefoldBuildProjectAction : AbstractDefoldBuildAction(
    text = "Build Project",
    description = "Build the Defold project",
    icon = AllIcons.Actions.Compile,
    buildCommands = listOf("build"),
)

class DefoldRebuildProjectAction : AbstractDefoldBuildAction(
    text = "Rebuild Project",
    description = "Clean and build the Defold project",
    icon = AllIcons.Actions.Rebuild,
    buildCommands = listOf("distclean", "build"),
)
