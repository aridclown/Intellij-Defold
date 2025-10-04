package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.aridclown.intellij.defold.ui.DefoldLogHyperlinkFilter
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provides access to the project's Defold-specific metadata.
 */
@Service(PROJECT)
class DefoldProjectService(private val project: Project) {

    val gameProjectFile: VirtualFile?
        get() = project.guessProjectDir()?.findChild(GAME_PROJECT_FILE)

    val editorConfig: DefoldEditorConfig? by lazy {
        DefoldEditorConfig.loadEditorConfig()
    }

    companion object {
        fun Project.defoldProjectService(): DefoldProjectService = service<DefoldProjectService>()

        val Project?.isDefoldProject: Boolean
            get() = this?.defoldProjectService()?.gameProjectFile != null
        val Project?.rootProjectFolder: VirtualFile?
            get() = this?.defoldProjectService()?.gameProjectFile?.parent
        val Project?.defoldVersion: String?
            get() = this?.defoldProjectService()?.editorConfig?.version

        fun Project.findActiveConsole(): ConsoleView? =
            RunContentManager.getInstance(this).selectedContent?.executionConsole as? ConsoleView

        fun Project.createConsole(): ConsoleView = TextConsoleBuilderFactory.getInstance()
            .createBuilder(this)
            .console
            .also { it.addMessageFilter(DefoldLogHyperlinkFilter(this)) }

        fun Project.ensureConsole(title: String): ConsoleView {
            findActiveConsole()?.let { return it }

            val app = ApplicationManager.getApplication()
            val manager = RunContentManager.getInstance(this)

            manager.allDescriptors
                .firstOrNull { it.displayName == title }
                ?.let { descriptor ->
                    val console = descriptor.executionConsole as? ConsoleView
                    if (console != null) {
                        val showExisting = Runnable {
                            manager.showRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor)
                        }
                        if (app.isDispatchThread) showExisting.run() else app.invokeAndWait(showExisting)
                        return console
                    }
                }

            val console = createConsole()
            val descriptor = RunContentDescriptor(console, null, console.component, title)
            val showNew = Runnable {
                manager.showRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor)
            }
            if (app.isDispatchThread) showNew.run() else app.invokeAndWait(showNew)
            return console
        }
    }
}