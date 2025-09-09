package com.aridclown.intellij.defold.ui

import com.aridclown.intellij.defold.DefoldProjectService.Companion.getService
import com.aridclown.intellij.defold.ProcessExecutor
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalBox
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

internal class DefoldToolWindowFactory : ToolWindowFactory, DumbAware {
    override suspend fun isApplicableAsync(project: Project): Boolean = true

    override fun shouldBeAvailable(project: Project): Boolean = project.getService().hasGameProjectFile()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = createContent(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun createContent(project: Project): JComponent {
        val mainPanel = JPanel(BorderLayout())

        // Create a console for output
        val console = ConsoleViewImpl(project, true)
        val processExecutor = ProcessExecutor(console)

        // Create control panel with buttons
        val controlPanel = VerticalBox().apply {
            add(JBLabel("Defold toolkit"))

            // Add Open Defold Editor button
            val openEditorButton = JButton("Open Defold Editor").apply {
                addActionListener {
                    openDefoldEditor(project, console, processExecutor)
                }
            }
            add(openEditorButton)
        }

        // Layout
        mainPanel.add(controlPanel, BorderLayout.NORTH)
        mainPanel.add(JBScrollPane(console.component), BorderLayout.CENTER)

        return mainPanel
    }

    private fun openDefoldEditor(
        project: Project,
        console: ConsoleViewImpl,
        processExecutor: ProcessExecutor
    ) {
        val defoldService = project.getService()
        val editorConfig = defoldService.getEditorConfig()

        if (editorConfig == null) {
            console.print("Defold editor configuration not found. Please ensure Defold is installed.\n", ERROR_OUTPUT)
            return
        }

        val projectFolder = defoldService.getDefoldProjectFolder()
        if (projectFolder == null) {
            console.print("No Defold project detected in current workspace.\n", ERROR_OUTPUT)
            return
        }

        DefoldEditorLauncher(console, processExecutor)
            .openDefoldEditor(projectFolder.path)
    }
}
