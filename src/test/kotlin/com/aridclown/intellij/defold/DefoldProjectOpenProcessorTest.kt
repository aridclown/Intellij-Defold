package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class DefoldProjectOpenProcessorTest {

    private val processor = DefoldProjectOpenProcessor()

    @Test
    fun `can open project when selecting game project file`() {
        val file = gameProjectFile()

        assertThat(processor.canOpenProject(file)).isTrue
    }

    @Test
    fun `can open project when selecting folder containing game project`() {
        val directory = directoryVirtualFile(children = mapOf(GAME_PROJECT_FILE to mockk()))

        assertThat(processor.canOpenProject(directory)).isTrue
    }

    @Test
    fun `cannot open project when folder lacks game project`() {
        val directory = directoryVirtualFile()

        assertThat(processor.canOpenProject(directory)).isFalse
    }

    @Test
    fun `opens parent directory when game project file selected`(@TempDir tempDir: Path) {
        val expectedPath = Files.createDirectories(tempDir.resolve("defold"))
        val parent = directoryVirtualFile(path = expectedPath)
        val file = gameProjectFile(parent)
        val project = mockk<Project>()

        withMockedProjectManager(project) { captured ->
            val opened = runInEdtAndGet {
                processor.doOpenProject(file, null, true)
            }

            assertThat(captured.paths).containsExactly(expectedPath)
            assertThat(captured.options)
                .singleElement()
                .extracting("isNewProject").isEqualTo(true)
            assertThat(opened === project).isTrue
        }
    }

    @Test
    fun `opens directory directly when folder selected`(@TempDir tempDir: Path) {
        val expectedPath = Files.createDirectories(tempDir.resolve("defold"))
        val directory = directoryVirtualFile(
            path = expectedPath,
            children = mapOf(GAME_PROJECT_FILE to mockk())
        )

        withMockedProjectManager(openResult = null) { captured ->
            runInEdtAndWait {
                processor.doOpenProject(directory, null, false)
            }

            assertThat(captured.paths).containsExactly(expectedPath)
            assertThat(captured.options)
                .singleElement()
                .extracting("isNewProject").isEqualTo(true)
        }
    }

    @Test
    fun `recognizes existing idea folder and keeps project flagged as existing`(@TempDir tempDir: Path) {
        val expectedPath = Files.createDirectories(tempDir.resolve("defold"))
        Files.createDirectories(expectedPath.resolve(DIRECTORY_STORE_FOLDER))
        val directory = directoryVirtualFile(
            path = expectedPath,
            children = mapOf(GAME_PROJECT_FILE to mockk())
        )

        withMockedProjectManager(openResult = null) { captured ->
            runInEdtAndWait {
                processor.doOpenProject(directory, null, false)
            }

            assertThat(captured.paths).containsExactly(expectedPath)
            assertThat(captured.options)
                .singleElement()
                .extracting("isNewProject").isEqualTo(false)
        }
    }

    private fun directoryVirtualFile(
        path: Path? = null,
        children: Map<String, VirtualFile?> = emptyMap()
    ): VirtualFile = mockk {
        every { isDirectory } returns true
        every { isValid } returns true
        every { findChild(any()) } answers { children[firstArg()] }
        if (path != null) {
            every { toNioPath() } returns path
        }
    }

    private fun gameProjectFile(parent: VirtualFile? = null): VirtualFile = mockk {
        every { isDirectory } returns false
        every { name } returns GAME_PROJECT_FILE
        every { isValid } returns true
        every { this@mockk.parent } returns parent
    }

    private inline fun <T> withMockedProjectManager(
        openResult: Project?,
        block: (CapturedOpenProjectCall) -> T
    ): T {
        mockkObject(ProjectManagerEx.Companion)
        val paths = mutableListOf<Path>()
        val options = mutableListOf<OpenProjectTask>()
        val manager = mockk<ProjectManagerEx>()
        every { ProjectManagerEx.getInstanceEx() } returns manager
        every { manager.openProject(any<Path>(), capture(options)) } answers {
            paths.add(firstArg<Path>())
            openResult
        }

        return try {
            block(CapturedOpenProjectCall(paths, options))
        } finally {
            unmockkObject(ProjectManagerEx.Companion)
        }
    }

    private data class CapturedOpenProjectCall(
        val paths: MutableList<Path>,
        val options: MutableList<OpenProjectTask>
    )
}
