package com.aridclown.intellij.defold.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalBox
import com.intellij.ui.content.ContentFactory
import com.aridclown.intellij.defold.DefoldProjectService
import javax.swing.JComponent

internal class DefoldToolWindowFactory : ToolWindowFactory, DumbAware {
    override suspend fun isApplicableAsync(project: Project): Boolean {
        return true
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return DefoldProjectService.getInstance(project).detect()
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = createContent(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun createContent(project: Project): JComponent = VerticalBox().apply {
        add(JBLabel("Defold project detected. Toolkit is initializing..."))
    }
}
