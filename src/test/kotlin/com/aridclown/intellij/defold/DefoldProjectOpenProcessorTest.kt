package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

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
    fun `opens parent directory when game project file selected`() {
        val expectedPath = Path.of("/tmp/defold")
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

        mockkStatic(ProjectUtil::class)
        val project = mockk<Project>()
        val captured = mutableListOf<Path>()
        every { ProjectUtil.openOrImport(any<Path>(), any(), any()) } answers {
            captured.add(firstArg<Path>())
            project
        }

        val opened = try {
            processor.doOpenProject(file, null, true)
        } finally {
            unmockkStatic(ProjectUtil::class)
        }

        assertThat(listOf(expectedPath)).isEqualTo(captured)
        assertThat(opened === project).isTrue
    }

    @Test
    fun `opens directory directly when folder selected`() {
        val expectedPath = Path.of("/tmp/defold")
        val directory = mockk<VirtualFile> {
            every { isDirectory } returns true
            every { findChild(GAME_PROJECT_FILE) } returns mockk()
            every { toNioPath() } returns expectedPath
            every { isValid } returns true
        }

        mockkStatic(ProjectUtil::class)
        val captured = mutableListOf<Path>()
        every { ProjectUtil.openOrImport(any<Path>(), any(), any()) } answers {
            captured.add(firstArg<Path>())
            null
        }

        try {
            processor.doOpenProject(directory, null, false)
        } finally {
            unmockkStatic(ProjectUtil::class)
        }

        assertThat(listOf(expectedPath)).isEqualTo(captured)
    }
}
