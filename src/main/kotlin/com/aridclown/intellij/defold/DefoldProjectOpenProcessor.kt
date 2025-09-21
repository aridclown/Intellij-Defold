package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.openapi.vfs.VirtualFile

class DefoldProjectOpenProcessor : ProjectOpenProcessor() {

    override val name: String = "Defold"

    override fun canOpenProject(file: VirtualFile): Boolean {
        if (file.isDirectory) {
            return file.findChild(GAME_PROJECT_FILE) != null
        }

        return file.name.equals(GAME_PROJECT_FILE, ignoreCase = false)
    }

    override fun doOpenProject(
        virtualFile: VirtualFile,
        projectToClose: Project?,
        forceOpenInNewFrame: Boolean
    ): Project? {
        val projectDir = when {
            virtualFile.isDirectory -> virtualFile
            virtualFile.name.equals(GAME_PROJECT_FILE, ignoreCase = false) -> virtualFile.parent
            else -> null
        } ?: return null

        return ProjectUtil.openOrImport(projectDir.toNioPath(), projectToClose, forceOpenInNewFrame)
    }
}
