package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

@Service(PROJECT)
class DefoldProjectService(private val project: Project) {

    private val editorConfig = DefoldEditorConfig.loadEditorConfig()

    fun hasGameProjectFile(): Boolean = findGameProjectFile() != null

    fun getDefoldVersion(): String? = editorConfig?.version

    fun getEditorConfig(): DefoldEditorConfig? = editorConfig

    fun getDefoldProjectFolder(): VirtualFile? = findGameProjectFile()?.parent

    private fun findGameProjectFile(): VirtualFile? {
        val roots = ProjectRootManager.getInstance(project).contentRoots
        for (root in roots) {
            root.findChild(GAME_PROJECT_FILE)?.let { return it }
            VfsUtil.findRelativeFile(root, "app", GAME_PROJECT_FILE)?.let { return it }
        }
        return null
    }

    companion object {
        fun Project.getService(): DefoldProjectService = service<DefoldProjectService>()
    }
}
