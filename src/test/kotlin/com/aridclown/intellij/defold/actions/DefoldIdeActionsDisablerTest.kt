package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldProjectService
import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldProjectService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefoldIdeActionsDisablerTest {
    private val compileDirty = RecordingAction()
    private val makeModule = RecordingAction()
    private val compile = RecordingAction()
    private val compileFile = RecordingAction()
    private val compileProject = RecordingAction()
    private val buildArtifact = RecordingAction()
    private val buildMenu = RecordingGroup()

    private val registeredActions =
        mutableMapOf(
            "BuildMenu" to buildMenu as AnAction,
            "CompileDirty" to compileDirty,
            "MakeModule" to makeModule,
            "Compile" to compile,
            "CompileFile" to compileFile,
            "CompileProject" to compileProject,
            "BuildArtifact" to buildArtifact
        )

    private val actionManager = mockk<ActionManagerEx>()
    private val project = mockk<Project>(relaxed = true)
    private val projectService = mockk<DefoldProjectService>()
    private val defoldMarker = mockk<VirtualFile>()
    private val event = mockk<AnActionEvent>(relaxed = true)
    private val presentation = Presentation()

    private var isDefoldProject = false

    @BeforeEach
    fun setUp() {
        mockkObject(DefoldProjectService.Companion)
        every { project.defoldProjectService() } returns projectService
        every { projectService.gameProjectFile } answers { if (isDefoldProject) defoldMarker else null }

        every { event.project } returns project
        every { event.presentation } returns presentation

        every { actionManager.getAction(any()) } answers { registeredActions[firstArg()] }
        every { actionManager.replaceAction(any(), any()) } answers {
            registeredActions[firstArg()] = secondArg()
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `installs wrappers only once`() {
        DefoldIdeActionsDisabler.install(actionManager)
        DefoldIdeActionsDisabler.install(actionManager)

        registeredActions.keys.forEach { targetActionId ->
            verify(exactly = 1) { actionManager.replaceAction(targetActionId, any()) }
        }
    }

    @Test
    fun `wrapped actions delegate update unless project is defold`() {
        DefoldIdeActionsDisabler.install(actionManager)
        val wrappedAction = registeredActions.getValue("Compile")

        isDefoldProject = false
        presentation.isEnabled = true
        presentation.isVisible = true
        wrappedAction.update(event)

        assertThat(compile.updateCount).isOne
        assertThat(presentation.isEnabledAndVisible).isTrue

        isDefoldProject = true
        presentation.isEnabled = true
        presentation.isVisible = true
        wrappedAction.update(event)

        assertThat(compile.updateCount).isOne // delegate not invoked again
        assertThat(presentation.isEnabledAndVisible).isFalse
    }

    private class RecordingAction : AnAction() {
        var updateCount = 0

        override fun actionPerformed(event: AnActionEvent) = Unit

        override fun update(event: AnActionEvent) {
            updateCount += 1
        }
    }

    private class RecordingGroup : DefaultActionGroup() {
        var updateCount = 0

        override fun update(event: AnActionEvent) {
            updateCount += 1
        }
    }
}
