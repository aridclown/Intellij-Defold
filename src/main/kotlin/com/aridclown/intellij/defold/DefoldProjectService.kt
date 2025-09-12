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

    // Defold project detection result cached to avoid expensive file system operations on every access.
    private val gameProjectFile: VirtualFile? by lazy { findGameProjectFile() }
    private val isDefoldProject: Boolean by lazy { gameProjectFile != null }
    private val rootProjectFolder: VirtualFile? by lazy { gameProjectFile?.parent }

    fun hasGameProjectFile(): Boolean = isDefoldProject

    fun getDefoldVersion(): String? = editorConfig?.version

    fun getEditorConfig(): DefoldEditorConfig? = editorConfig

    fun getDefoldProjectFolder(): VirtualFile? = rootProjectFolder

    private fun findGameProjectFile(): VirtualFile? = ProjectRootManager.getInstance(project)
        .contentRoots
        .firstNotNullOfOrNull { root ->
            root.findChild(GAME_PROJECT_FILE)
                ?: VfsUtil.findRelativeFile(root, "app", GAME_PROJECT_FILE)
        }

    companion object {
        fun Project.getService(): DefoldProjectService = service<DefoldProjectService>()
    }
}