package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.aridclown.intellij.defold.logging.LogHyperlinkFilter
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.Application
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
class DefoldProjectService(
    private val project: Project
) {
    val gameProjectFile: VirtualFile?
        get() = project.guessProjectDir()?.findChild(GAME_PROJECT_FILE)

    val editorConfig: DefoldEditorConfig?
        get() = DefoldEditorConfig.loadEditorConfig()

    companion object {
        fun Project.defoldProjectService(): DefoldProjectService = service<DefoldProjectService>()

        val Project?.isDefoldProject: Boolean
            get() = this?.defoldProjectService()?.gameProjectFile != null
        val Project?.rootProjectFolder: VirtualFile?
            get() = this?.defoldProjectService()?.gameProjectFile?.parent
        val Project?.defoldVersion: String?
            get() = this?.defoldProjectService()?.editorConfig?.version

        fun Project.findActiveConsole(): ConsoleView? = RunContentManager.getInstance(this).selectedContent?.executionConsole as? ConsoleView

        fun Project.ensureConsole(title: String): ConsoleView {
            findActiveConsole()?.let { return it }

            val app: Application = ApplicationManager.getApplication()
            val manager = RunContentManager.getInstance(this)

            manager.allDescriptors
                .firstOrNull { it.displayName == title }
                ?.takeIf { it.executionConsole != null }
                ?.let { descriptor ->
                    manager.runOrInvoke(app, descriptor)
                    return descriptor.executionConsole as ConsoleView
                }

            return createConsole().apply {
                val descriptor = RunContentDescriptor(this, null, component, title)
                manager.runOrInvoke(app, descriptor)
            }
        }

        fun Project.createConsole(): ConsoleView = TextConsoleBuilderFactory
            .getInstance()
            .createBuilder(this)
            .console
            .also { it.addMessageFilter(LogHyperlinkFilter(this)) }

        private fun RunContentManager.runOrInvoke(
            app: Application,
            descriptor: RunContentDescriptor
        ) {
            val showExisting =
                Runnable {
                    showRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor)
                }
            if (app.isDispatchThread) showExisting.run() else app.invokeAndWait(showExisting)
        }
    }
}
