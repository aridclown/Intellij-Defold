package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldPathResolver
import com.aridclown.intellij.defold.DefoldProjectService
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.debugger.DefoldRunConfigurationUtil
import com.aridclown.intellij.defold.debugger.MobDebugRunConfiguration
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.junit5.TestApplication
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class BuildActionsIntegrationTest {

    private val project = mockk<Project>(relaxed = true)
    private val event = mockk<AnActionEvent>(relaxed = true)
    private val presentation = mockk<Presentation>(relaxed = true)
    private val runConfiguration = mockk<MobDebugRunConfiguration>(relaxed = true)
    private val settings = mockk<RunnerAndConfigurationSettings>(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkObject(DefoldPathResolver)
        mockkObject(DefoldRunConfigurationUtil)
        mockkStatic(ProgramRunnerUtil::class)
        mockkStatic(DefaultRunExecutor::class)
        mockkObject(DefoldProjectService.Companion)

        every { project.isDefoldProject } returns true
        every { event.project } returns project
        every { event.presentation } returns presentation
        every { settings.configuration } returns runConfiguration
        every { DefoldRunConfigurationUtil.getOrCreate(project) } returns settings
        every { DefaultRunExecutor.getRunExecutorInstance() } returns mockk(relaxed = true)
        every { ProgramRunnerUtil.executeConfiguration(any(), any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `build project action triggers build with correct command`() {
        every { DefoldPathResolver.ensureEditorConfig(project) } returns mockk()

        BuildProjectAction().actionPerformed(event)

        verify { runConfiguration.runtimeBuildCommands = listOf("build") }
        verify { runConfiguration.runtimeEnableDebugScript = true }
        verify { ProgramRunnerUtil.executeConfiguration(settings, any()) }
    }

    @Test
    fun `clean build project action triggers clean build after confirmation`() {
        every { DefoldPathResolver.ensureEditorConfig(project) } returns mockk()

        TestDialogManager.setTestDialog(TestDialog.OK)

        CleanBuildProjectAction().actionPerformed(event)

        verify { runConfiguration.runtimeBuildCommands = listOf("distclean", "build") }
        verify { runConfiguration.runtimeEnableDebugScript = true }
        verify { ProgramRunnerUtil.executeConfiguration(settings, any()) }
    }

    @Test
    fun `clean build project action skips build when user cancels confirmation`() {
        TestDialogManager.setTestDialog(TestDialog.NO)

        CleanBuildProjectAction().actionPerformed(event)

        verify(exactly = 0) { runConfiguration.runtimeBuildCommands = any() }
        verify(exactly = 0) { ProgramRunnerUtil.executeConfiguration(any(), any()) }
    }

    @Test
    fun `action is enabled and visible for Defold projects`() {
        val action = BuildProjectAction()
        action.update(event)

        verify { presentation.isEnabledAndVisible = true }
    }

    @Test
    fun `action does nothing when project is not Defold`() {
        every { project.isDefoldProject } returns false

        val action = BuildProjectAction()
        action.actionPerformed(event)

        verify(exactly = 0) { DefoldPathResolver.ensureEditorConfig(any()) }
        verify(exactly = 0) { ProgramRunnerUtil.executeConfiguration(any(), any()) }
    }

    @Test
    fun `action does nothing when project is null`() {
        every { event.project } returns null

        BuildProjectAction().actionPerformed(event)

        verify(exactly = 0) { DefoldPathResolver.ensureEditorConfig(any()) }
        verify(exactly = 0) { ProgramRunnerUtil.executeConfiguration(any(), any()) }
    }

    @Test
    fun `action does nothing when editor config resolution fails`() {
        every { DefoldPathResolver.ensureEditorConfig(project) } returns null

        BuildProjectAction().actionPerformed(event)

        verify(exactly = 0) { ProgramRunnerUtil.executeConfiguration(any(), any()) }
    }

    @Test
    fun `action does nothing when run configuration is not MobDebugRunConfiguration`() {
        every { DefoldPathResolver.ensureEditorConfig(project) } returns mockk()
        every { settings.configuration } returns mockk(relaxed = true)

        BuildProjectAction().actionPerformed(event)

        verify(exactly = 0) { ProgramRunnerUtil.executeConfiguration(any(), any()) }
    }

    @Test
    fun `build actions uses background thread for updates`() {
        assertThat(BuildProjectAction().getActionUpdateThread()).isEqualTo(BGT)
        assertThat(CleanBuildProjectAction().getActionUpdateThread()).isEqualTo(BGT)
    }
}