package com.aridclown.intellij.defold

import com.intellij.openapi.project.Project
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DependencyResolverTest {
    private val project = mockk<Project>(relaxed = true)
    private val config = mockk<DefoldEditorConfig>(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkConstructor(ProjectBuilder::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `builds project with resolve command`() = runTest {
        val requestSlot = slot<BuildRequest>()
        val messageSlot = slot<String>()
        coEvery {
            anyConstructed<ProjectBuilder>().buildProject(capture(requestSlot), capture(messageSlot))
        } returns Result.success(Unit)

        DependencyResolver.resolve(project, config)

        coVerify(exactly = 1) { anyConstructed<ProjectBuilder>().buildProject(any(), any()) }
        assertThat(requestSlot.captured.commands).containsExactly("resolve")
        assertThat(requestSlot.captured.project).isEqualTo(project)
        assertThat(requestSlot.captured.config).isEqualTo(config)
        assertThat(messageSlot.captured).isEqualTo(RESOLVE_BUILD_MESSAGE)
    }
}
