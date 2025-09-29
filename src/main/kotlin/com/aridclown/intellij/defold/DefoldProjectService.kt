package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provides access to the project's Defold-specific metadata.
 * Defold project detection result cached to avoid expensive file system operations on every access.
 */
@Service(PROJECT)
class DefoldProjectService(private val project: Project) {

    val editorConfig: DefoldEditorConfig? by lazy { DefoldEditorConfig.loadEditorConfig() }
    val gameProjectFile: VirtualFile? by lazy { findGameProjectFile() }
    val isDefoldProject: Boolean = gameProjectFile != null
    val rootProjectFolder: VirtualFile? = gameProjectFile?.parent
    val defoldVersion: String? = editorConfig?.version

    private fun findGameProjectFile(): VirtualFile? = ProjectRootManager.getInstance(project)
        .contentRoots
        .firstNotNullOfOrNull { root ->
            root.findChild(GAME_PROJECT_FILE)
                ?: VfsUtil.findRelativeFile(root, "app", GAME_PROJECT_FILE)
        }

    companion object {
        fun Project.defoldProjectService(): DefoldProjectService = service<DefoldProjectService>()
    }
}