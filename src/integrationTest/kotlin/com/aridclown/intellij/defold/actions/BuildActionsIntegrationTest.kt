package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldPathResolver
import com.aridclown.intellij.defold.DefoldProjectService
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.debugger.DefoldRunConfigurationUtil
import com.aridclown.intellij.defold.debugger.MobDebugRunConfiguration
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class BuildActionsIntegrationTest {
    @JvmField
    @RegisterExtension
    val projectModel = ProjectModelExtension()

    private lateinit var project: Project
    private lateinit var runManager: RunManager
    private val event = mockk<AnActionEvent>(relaxed = true)
    private val presentation = mockk<Presentation>(relaxed = true)

    @BeforeEach
    fun setUp() {
        project = projectModel.project
        runManager = RunManager.getInstance(project)

        mockkObject(DefoldPathResolver)
        mockkObject(DefoldProjectService.Companion)
        mockkStatic(ProgramRunnerUtil::class)
        mockkStatic(DefaultRunExecutor::class)

        every { project.isDefoldProject } returns true
        every { event.project } returns project
        every { event.presentation } returns presentation
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

        val settingsSlot = slot<RunnerAndConfigurationSettings>()
        every { ProgramRunnerUtil.executeConfiguration(capture(settingsSlot), any()) } just Runs

        BuildProjectAction().actionPerformed(event)

        verify { ProgramRunnerUtil.executeConfiguration(any(), any()) }

        val config = settingsSlot.captured.configuration as MobDebugRunConfiguration
        assertThat(config.runtimeBuildCommands).isEqualTo(listOf("build"))
        assertThat(config.runtimeEnableDebugScript).isTrue
    }

    @Test
    fun `clean build project action triggers clean build after confirmation`() {
        every { DefoldPathResolver.ensureEditorConfig(project) } returns mockk()

        val settingsSlot = slot<RunnerAndConfigurationSettings>()
        every { ProgramRunnerUtil.executeConfiguration(capture(settingsSlot), any()) } just Runs

        TestDialogManager.setTestDialog(TestDialog.OK)

        CleanBuildProjectAction().actionPerformed(event)

        verify { ProgramRunnerUtil.executeConfiguration(any(), any()) }

        val config = settingsSlot.captured.configuration as MobDebugRunConfiguration
        assertThat(config.runtimeBuildCommands).isEqualTo(listOf("distclean", "build"))
        assertThat(config.runtimeEnableDebugScript).isTrue
    }

    @Test
    fun `clean build project action skips build when user cancels confirmation`() {
        val initialConfigsCount = runManager.allSettings.size

        TestDialogManager.setTestDialog(TestDialog.NO)

        CleanBuildProjectAction().actionPerformed(event)

        assertThat(runManager.allSettings).hasSize(initialConfigsCount)
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

        mockkObject(DefoldRunConfigurationUtil)
        val mockSettings = mockk<RunnerAndConfigurationSettings>(relaxed = true)
        every { mockSettings.configuration } returns mockk(relaxed = true)
        every { DefoldRunConfigurationUtil.getOrCreate(project) } returns mockSettings

        BuildProjectAction().actionPerformed(event)

        verify(exactly = 0) { ProgramRunnerUtil.executeConfiguration(any(), any()) }
    }

    @Test
    fun `build actions uses background thread for updates`() {
        assertThat(BuildProjectAction().getActionUpdateThread()).isEqualTo(BGT)
        assertThat(CleanBuildProjectAction().getActionUpdateThread()).isEqualTo(BGT)
    }
}
