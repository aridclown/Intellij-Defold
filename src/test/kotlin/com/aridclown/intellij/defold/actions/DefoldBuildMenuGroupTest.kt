package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldProjectService
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefoldBuildMenuGroupTest {
    private val project = mockk<Project>(relaxed = true)
    private val event = mockk<AnActionEvent>(relaxed = true)
    private val presentation = mockk<Presentation>(relaxed = true)
    private val defoldMenuGroup = DefoldProjectMenuGroup()

    @BeforeEach
    fun setUp() {
        mockkObject(DefoldProjectService.Companion)

        every { event.project } returns project
        every { event.presentation } returns presentation
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `action is invisible when project is not Defold`() {
        every { project.isDefoldProject } returns false

        defoldMenuGroup.update(event)

        verify { presentation.isVisible = false }
    }
}
