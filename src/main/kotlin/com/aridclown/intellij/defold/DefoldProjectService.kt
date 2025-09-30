package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.aridclown.intellij.defold.ui.DefoldLogHyperlinkFilter
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provides access to the project's Defold-specific metadata.
 * Defold project detection result cached to avoid expensive file system operations on every access.
 */
@Service(PROJECT)
class DefoldProjectService(private val project: Project) {

    val gameProjectFile: VirtualFile? by lazy {
        ProjectRootManager.getInstance(project)
            .contentRoots
            .firstNotNullOfOrNull { it.findChild(GAME_PROJECT_FILE) }
    }

    val editorConfig: DefoldEditorConfig? by lazy {
        DefoldEditorConfig.loadEditorConfig()
    }

    val isDefoldProject: Boolean = gameProjectFile != null
    val rootProjectFolder: VirtualFile? = gameProjectFile?.parent
    val defoldVersion: String? = editorConfig?.version

    companion object {
        fun Project.defoldProjectService(): DefoldProjectService = service<DefoldProjectService>()

        fun Project.findActiveConsole(): ConsoleView? =
            RunContentManager.getInstance(this).selectedContent?.executionConsole as? ConsoleView

        fun Project.createConsole(): ConsoleView = TextConsoleBuilderFactory.getInstance()
            .createBuilder(this)
            .console
            .also { it.addMessageFilter(DefoldLogHyperlinkFilter(this)) }
    }
}