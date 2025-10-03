package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor

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
        val projectDir = if (virtualFile.isDirectory) {
            virtualFile
        } else if (virtualFile.name.equals(GAME_PROJECT_FILE, ignoreCase = false)) {
            virtualFile.parent
        } else {
            null
        }

        if (projectDir == null) return null

        val projectManager = ProjectManagerEx.getInstanceEx()
        val nioPath = projectDir.toNioPath()
        val isFreshProject = projectDir.findChild(DIRECTORY_STORE_FOLDER) == null

        var openOptions = OpenProjectTask
            .build()
            .withForceOpenInNewFrame(forceOpenInNewFrame)
            .withProjectToClose(projectToClose)

        if (isFreshProject) {
            openOptions = openOptions.asNewProject()
        }

        return projectManager.openProject(nioPath, openOptions)
    }
}
