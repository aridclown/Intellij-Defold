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
import java.nio.file.Path
import java.nio.file.Files

@TestApplication
class DefoldProjectOpenProcessorTest {

    private val processor = DefoldProjectOpenProcessor()

    @Test
    fun `can open project when selecting game project file`() {
        val file = mockk<VirtualFile> {
            every { isDirectory } returns false
            every { name } returns GAME_PROJECT_FILE
            every { isValid } returns true
        }

        assertThat(processor.canOpenProject(file)).isTrue
    }

    @Test
    fun `can open project when selecting folder containing game project`() {
        val gameProject = mockk<VirtualFile>()
        val directory = mockk<VirtualFile> {
            every { isDirectory } returns true
            every { findChild(GAME_PROJECT_FILE) } returns gameProject
            every { isValid } returns true
        }

        assertThat(processor.canOpenProject(directory)).isTrue
    }

    @Test
    fun `cannot open project when folder lacks game project`() {
        val directory = mockk<VirtualFile> {
            every { isDirectory } returns true
            every { findChild(GAME_PROJECT_FILE) } returns null
            every { isValid } returns true
        }

        assertThat(processor.canOpenProject(directory)).isFalse
    }

    @Test
    fun `opens parent directory when game project file selected`(@TempDir tempDir: Path) {
        val expectedPath = Files.createDirectories(tempDir.resolve("defold"))
        val parent = mockk<VirtualFile> {
            every { toNioPath() } returns expectedPath
            every { isValid } returns true
        }
        val file = mockk<VirtualFile> {
            every { isDirectory } returns false
            every { name } returns GAME_PROJECT_FILE
            every { this@mockk.parent } returns parent
            every { isValid } returns true
        }

        mockkObject(ProjectManagerEx.Companion)
        val project = mockk<Project>()
        val captured = mutableListOf<Path>()
        val capturedOptions = mutableListOf<OpenProjectTask>()
        val manager = mockk<ProjectManagerEx>()
        every { ProjectManagerEx.getInstanceEx() } returns manager
        every { ProjectManagerEx.Companion.getInstanceEx() } returns manager
        every { manager.openProject(any<Path>(), capture(capturedOptions)) } answers {
            captured.add(firstArg<Path>())
            project
        }

        val opened = try {
            runInEdtAndGet {
                processor.doOpenProject(file, null, true)
            }
        } finally {
            unmockkObject(ProjectManagerEx.Companion)
        }

        assertThat(listOf(expectedPath)).isEqualTo(captured)
        assertThat(capturedOptions)
            .singleElement()
            .extracting("isNewProject").isEqualTo(true)
        assertThat(opened === project).isTrue
    }

    @Test
    fun `opens directory directly when folder selected`(@TempDir tempDir: Path) {
        val expectedPath = Files.createDirectories(tempDir.resolve("defold"))
        val directory = mockk<VirtualFile> {
            every { isDirectory } returns true
            every { findChild(GAME_PROJECT_FILE) } returns mockk()
            every { toNioPath() } returns expectedPath
            every { isValid } returns true
        }

        mockkObject(ProjectManagerEx.Companion)
        val captured = mutableListOf<Path>()
        val capturedOptions = mutableListOf<OpenProjectTask>()
        val manager = mockk<ProjectManagerEx>()
        every { ProjectManagerEx.getInstanceEx() } returns manager
        every { ProjectManagerEx.Companion.getInstanceEx() } returns manager
        every { manager.openProject(any<Path>(), capture(capturedOptions)) } answers {
            captured.add(firstArg<Path>())
            null
        }

        try {
            runInEdtAndWait {
                processor.doOpenProject(directory, null, false)
            }
        } finally {
            unmockkObject(ProjectManagerEx.Companion)
        }

        assertThat(listOf(expectedPath)).isEqualTo(captured)
        assertThat(capturedOptions)
            .singleElement()
            .extracting("isNewProject").isEqualTo(true)
    }

    @Test
    fun `recognizes existing idea folder and keeps project flagged as existing`(@TempDir tempDir: Path) {
        val expectedPath = Files.createDirectories(tempDir.resolve("defold"))
        Files.createDirectories(expectedPath.resolve(DIRECTORY_STORE_FOLDER))
        val directory = mockk<VirtualFile> {
            every { isDirectory } returns true
            every { findChild(GAME_PROJECT_FILE) } returns mockk()
            every { toNioPath() } returns expectedPath
            every { isValid } returns true
        }

        mockkObject(ProjectManagerEx.Companion)
        val capturedOptions = mutableListOf<OpenProjectTask>()
        val manager = mockk<ProjectManagerEx>()
        every { ProjectManagerEx.getInstanceEx() } returns manager
        every { manager.openProject(any<Path>(), capture(capturedOptions)) } returns null

        try {
            runInEdtAndWait {
                processor.doOpenProject(directory, null, false)
            }
        } finally {
            unmockkObject(ProjectManagerEx.Companion)
        }

        assertThat(capturedOptions)
            .singleElement()
            .extracting("isNewProject").isEqualTo(false)
    }
}
