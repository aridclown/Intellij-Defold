package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldEditorLauncher
import com.aridclown.intellij.defold.DefoldPathResolver
import com.aridclown.intellij.defold.DefoldProjectService
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.DefoldProjectService.Companion.rootProjectFolder
import com.aridclown.intellij.defold.util.NotificationService
import com.aridclown.intellij.defold.util.NotificationService.notifyError
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.*
import kotlinx.coroutines.Job
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenDefoldEditorActionTest {

    private val project = mockk<Project>(relaxed = true)
    private val event = mockk<AnActionEvent>(relaxed = true)
    private val presentation = mockk<Presentation>(relaxed = true)
    private val projectFolder = mockk<VirtualFile>(relaxed = true)

    private val action = OpenDefoldEditorAction()

    private val projectPath = "/workspace/defold"

    @BeforeEach
    fun setUp() {
        mockkObject(DefoldProjectService.Companion)
        mockkObject(DefoldPathResolver)
        mockkObject(NotificationService)
        mockkConstructor(DefoldEditorLauncher::class)

        every { event.project } returns project
        every { event.presentation } returns presentation
        every { project.isDefoldProject } returns true
        every { project.rootProjectFolder } returns projectFolder
        every { projectFolder.path } returns projectPath
        every { DefoldPathResolver.ensureEditorConfig(project) } returns mockk(relaxed = true)
        every { anyConstructed<DefoldEditorLauncher>().openDefoldEditor(any()) } returns mockk<Job>(relaxed = true)
        justRun { project.notifyError(any(), any()) }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `launches editor when project is recognized as defold`() {
        action.actionPerformed(event)

        verify { anyConstructed<DefoldEditorLauncher>().openDefoldEditor(projectPath) }
    }

    @Test
    fun `ignores invocation when event carries no project`() {
        every { event.project } returns null

        action.actionPerformed(event)

        verify(exactly = 0) { anyConstructed<DefoldEditorLauncher>().openDefoldEditor(any()) }
    }

    @Test
    fun `does not launch editor for non defold project`() {
        every { project.isDefoldProject } returns false

        action.actionPerformed(event)

        verify(exactly = 0) { anyConstructed<DefoldEditorLauncher>().openDefoldEditor(any()) }
    }

    @Test
    fun `skips launch when editor configuration is missing`() {
        every { DefoldPathResolver.ensureEditorConfig(project) } returns null

        action.actionPerformed(event)

        verify(exactly = 0) { anyConstructed<DefoldEditorLauncher>().openDefoldEditor(any()) }
    }

    @Test
    fun `notifies user when project folder is absent`() {
        every { project.rootProjectFolder } returns null

        action.actionPerformed(event)

        verify { project.notifyError("Defold", "No Defold project detected in current workspace.") }
        verify(exactly = 0) { anyConstructed<DefoldEditorLauncher>().openDefoldEditor(any()) }
    }

    @Test
    fun `shows action when project is defold`() {
        action.update(event)

        verify { presentation.isEnabledAndVisible = true }
    }

    @Test
    fun `hides action when project is not defold`() {
        every { project.isDefoldProject } returns false

        action.update(event)

        verify { presentation.isEnabledAndVisible = false }
    }

    @Test
    fun `hides action when event has no project`() {
        every { event.project } returns null

        action.update(event)

        verify { presentation.isEnabledAndVisible = false }
    }

    @Test
    fun `signals background thread for updates`() {
        assertThat(action.actionUpdateThread).isEqualTo(BGT)
    }
}
