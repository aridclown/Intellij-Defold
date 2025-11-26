package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldCoroutineService
import com.aridclown.intellij.defold.DefoldProjectService
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.hotreload.HotReloadService
import com.aridclown.intellij.defold.hotreload.HotReloadService.Companion.hotReloadProjectService
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
class HotReloadActionTest {
    private val project = mockk<Project>(relaxed = true)
    private val event = mockk<AnActionEvent>(relaxed = true)
    private val presentation = mockk<Presentation>(relaxed = true)
    private val hotReloadService = mockk<HotReloadService>(relaxed = true)
    private val hotReloadAction = HotReloadAction()

    @BeforeEach
    fun setUp() {
        mockkObject(DefoldProjectService.Companion)
        mockkObject(HotReloadService.Companion)
        mockkStatic(FileDocumentManager::class)
        mockkStatic("com.intellij.openapi.application.CoroutinesKt")

        every { project.isDefoldProject } returns true
        every { project.hotReloadProjectService() } returns hotReloadService
        every { event.project } returns project
        every { event.presentation } returns presentation
        every { FileDocumentManager.getInstance() } returns mockk(relaxed = true)
        coJustRun { edtWriteAction(any()) }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `performs hot reload on action performed`() = runTest {
        val service = DefoldCoroutineService(this)
        every { project.service<DefoldCoroutineService>() } returns service

        hotReloadAction.actionPerformed(event)

        advanceUntilIdle()

        coVerify(exactly = 1) { FileDocumentManager.getInstance()::saveAllDocuments }
        coVerify(exactly = 1) { hotReloadService.performHotReload() }
    }

    @Test
    fun `does nothing when project is null on action performed`() {
        every { event.project } returns null

        hotReloadAction.actionPerformed(event)

        coVerify(exactly = 0) { FileDocumentManager.getInstance()::saveAllDocuments }
        coVerify(exactly = 0) { hotReloadService.performHotReload() }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `action is enabled only when engine is reachable`(enabled: Boolean) {
        every { hotReloadService.hasReachableEngine() } returns enabled

        hotReloadAction.update(event)

        verify { presentation.isEnabled = enabled }
    }

    @Test
    fun `action is disabled when project is null`() {
        every { event.project } returns null

        hotReloadAction.update(event)

        verify { presentation.isEnabled = false }
    }

    @Test
    fun `returns BGT for action update thread`() {
        assertThat(hotReloadAction.actionUpdateThread).isEqualTo(BGT)
    }
}
