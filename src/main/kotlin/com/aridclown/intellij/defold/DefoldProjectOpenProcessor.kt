package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.aridclown.intellij.defold.util.letIfNot
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.ide.progress.ModalTaskOwner.guess
import com.intellij.platform.ide.progress.TaskCancellation.Companion.nonCancellable
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.projectImport.ProjectOpenProcessor
import kotlin.io.path.exists

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
            virtualFile.isFile && virtualFile.name.equals(GAME_PROJECT_FILE, ignoreCase = false) -> virtualFile.parent
            else -> return null // Unsupported file type
        }.toNioPath()

        val openOptions = runWithModalProgressBlocking(
            owner = guess(),
            title = "Opening Defold project",
            cancellation = nonCancellable()
        ) {
            val isExistingProject = projectDir.resolve(DIRECTORY_STORE_FOLDER).exists()

            OpenProjectTask
                .build()
                .withForceOpenInNewFrame(forceOpenInNewFrame)
                .withProjectToClose(projectToClose)
                .letIfNot(isExistingProject, OpenProjectTask::asNewProject)
        }

        return ProjectManagerEx.getInstanceEx().openProject(projectDir, openOptions)
    }
}
