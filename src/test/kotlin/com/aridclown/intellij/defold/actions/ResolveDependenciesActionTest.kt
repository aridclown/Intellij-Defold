package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldCoroutineService
import com.aridclown.intellij.defold.DefoldEditorConfig
import com.aridclown.intellij.defold.DefoldPathResolver
import com.aridclown.intellij.defold.DefoldProjectService
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.DependencyResolver
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResolveDependenciesActionTest {
    private val project = mockk<Project>(relaxed = true)
    private val event = mockk<AnActionEvent>(relaxed = true)
    private val presentation = mockk<Presentation>(relaxed = true)
    private val config = mockk<DefoldEditorConfig>(relaxed = true)
    private val action = ResolveDependenciesAction()

    @BeforeEach
    fun setUp() {
        mockkObject(DefoldProjectService.Companion)
        mockkObject(DefoldPathResolver)
        mockkObject(DependencyResolver)

        every { event.project } returns project
        every { event.presentation } returns presentation
        every { project.isDefoldProject } returns true
        every { DefoldPathResolver.ensureEditorConfig(project) } returns config
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `runs resolve command when project is valid`() = runTest {
        val service = DefoldCoroutineService(this)
        every { project.service<DefoldCoroutineService>() } returns service
        coEvery { DependencyResolver.resolve(project, config) } returns Unit

        action.actionPerformed(event)
        advanceUntilIdle()

        coVerify(exactly = 1) { DependencyResolver.resolve(project, config) }
    }

    @Test
    fun `does nothing when project is not Defold`() = runTest {
        every { project.isDefoldProject } returns false

        action.actionPerformed(event)

        coVerify(exactly = 0) { DependencyResolver.resolve(any(), any()) }
    }

    @Test
    fun `does nothing when editor config is missing`() = runTest {
        every { DefoldPathResolver.ensureEditorConfig(project) } returns null

        action.actionPerformed(event)

        coVerify(exactly = 0) { DependencyResolver.resolve(any(), any()) }
    }

    @Test
    fun `does nothing when project is null`() = runTest {
        every { event.project } returns null

        action.actionPerformed(event)

        coVerify(exactly = 0) { DependencyResolver.resolve(any(), any()) }
    }

    @Test
    fun `update toggles visibility based on Defold project`() {
        action.update(event)
        verify { presentation.isEnabledAndVisible = true }

        every { project.isDefoldProject } returns false
        action.update(event)
        verify { presentation.isEnabledAndVisible = false }
    }

    @Test
    fun `uses background thread for updates`() {
        assertThat(action.actionUpdateThread).isEqualTo(BGT)
    }
}
