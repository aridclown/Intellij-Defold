package com.aridclown.intellij.defold.ui

import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DefoldLogHyperlinkFilterTest {

    private lateinit var filter: DefoldLogHyperlinkFilter
    private lateinit var mockProject: Project

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        mockProject = mockk()
        every { mockProject.basePath } returns tempDir.toString()

        mockkStatic(LocalFileSystem::class)
        every { LocalFileSystem.getInstance().findFileByPath(any()) } answers {
            val path = Path.of(firstArg<String>())
            if (Files.notExists(path)) {
                return@answers null
            }

            mockk {
                every { this@mockk.isValid } returns true
                every { this@mockk.path } returns "$path"
            }
        }

        filter = DefoldLogHyperlinkFilter(mockProject)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(LocalFileSystem::class)
    }

    private fun createDefoldFile(relativePath: String) {
        val filePath = tempDir.resolve(relativePath)
        filePath.parent?.let { Files.createDirectories(it) }
        if (Files.notExists(filePath)) {
            Files.createFile(filePath)
        }
    }

    private fun createDefoldFiles(vararg relativePaths: String) {
        relativePaths.forEach { createDefoldFile(it) }
    }

    @Test
    fun `handles simple script file with line number`() {
        val line = "ERROR:SCRIPT: main/abc/def/test.script:17: attempt to index global 'asd' (a nil value)"
        val entireLength = line.length

        createDefoldFile("main/abc/def/test.script")

        val result = filter.applyFilter(line, entireLength)

        assertThat(result).isNotNull()
        assertThat(result!!.resultItems).hasSize(1)

        val item = result.resultItems[0]
        assertThat(line.substring(item.highlightStartOffset, item.highlightEndOffset))
            .isEqualTo("main/abc/def/test.script:17")

        val hyperlink = item.hyperlinkInfo as OpenFileHyperlinkInfo
        assertThat(hyperlink).isNotNull()
    }

    @Test
    fun `handles lua file with line number`() {
        val line = "ERROR:SCRIPT: main/test.lua:4: attempt to index global 'asd' (a nil value)"
        val entireLength = line.length

        createDefoldFile("main/test.lua")

        val result = filter.applyFilter(line, entireLength)

        assertThat(result).isNotNull()
        assertThat(result!!.resultItems).hasSize(1)

        val item = result.resultItems[0]
        assertThat(line.substring(item.highlightStartOffset, item.highlightEndOffset))
            .isEqualTo("main/test.lua:4")
    }

    @Test
    fun `handles multiple file references in same line`() {
        val line = "  main/abc/def/test.script:24: in function <main/abc/def/test.script:16>"
        val entireLength = line.length

        createDefoldFile("main/abc/def/test.script")

        val result = filter.applyFilter(line, entireLength)

        assertThat(result).isNotNull()
        assertThat(result!!.resultItems).hasSize(2)

        val firstItem = result.resultItems[0]
        assertThat(line.substring(firstItem.highlightStartOffset, firstItem.highlightEndOffset))
            .isEqualTo("main/abc/def/test.script:24")

        val secondItem = result.resultItems[1]
        assertThat(line.substring(secondItem.highlightStartOffset, secondItem.highlightEndOffset))
            .isEqualTo("main/abc/def/test.script:16")
    }

    @Test
    fun `handles stack traceback with multiple files`() {
        val lines = listOf(
            "ERROR:SCRIPT: main/test.lua:4: attempt to index global 'asd' (a nil value)",
            "stack traceback:",
            "  main/test.lua:4: in function test2",
            "  def/test.script:20: in function test",
            "  def/test.script:117: in function <def/test.script:115>"
        )

        createDefoldFiles("main/test.lua", "def/test.script")

        // Test each line individually
        val errorLine = lines[0]
        val errorResult = filter.applyFilter(errorLine, errorLine.length)
        assertThat(errorResult!!.resultItems).hasSize(1)

        val traceLine1 = lines[2]
        val trace1Result = filter.applyFilter(traceLine1, traceLine1.length)
        assertThat(trace1Result!!.resultItems).hasSize(1)

        val traceLine2 = lines[3]
        val trace2Result = filter.applyFilter(traceLine2, traceLine2.length)
        assertThat(trace2Result!!.resultItems).hasSize(1)

        val traceLine3 = lines[4]
        val trace3Result = filter.applyFilter(traceLine3, traceLine3.length)
        assertThat(trace3Result!!.resultItems).hasSize(2) // Two file references in this line
    }

    @Test
    fun `handles all supported file extensions`() {
        val testCases = listOf(
            "error.script:10",
            "error.lua:20",
            "error.gui_script:30",
            "error.render_script:40",
            "error.editor_script:50"
        )

        testCases.forEach { fileRef ->
            val line = "Error in $fileRef: some error"
            createDefoldFile(fileRef.substringBefore(':'))
            val result = filter.applyFilter(line, line.length)

            assertThat(result).isNotNull()
            assertThat(result!!.resultItems).hasSize(1)

            val item = result.resultItems[0]
            assertThat(line.substring(item.highlightStartOffset, item.highlightEndOffset))
                .isEqualTo(fileRef)
        }
    }

    @Test
    fun `ignores lines without file references`() {
        val lines = listOf(
            "stack traceback:",
            "Some random log message",
            "ERROR: Something went wrong but no file reference",
            "DEBUG: Normal debug message"
        )

        lines.forEach { line ->
            val result = filter.applyFilter(line, line.length)
            assertThat(result).isNull()
        }
    }

    @Test
    fun `ignores unsupported file extensions`() {
        val lines = listOf(
            "error.txt:10: some error",
            "error.json:20: some error",
            "error.xml:30: some error"
        )

        lines.forEach { line ->
            val result = filter.applyFilter(line, line.length)
            assertThat(result).isNull()
        }
    }

    @Test
    fun `handles complex file paths`() {
        val line = "ERROR: deeply/nested/path/with-dashes/and_underscores/file.script:123: error"
        createDefoldFile("deeply/nested/path/with-dashes/and_underscores/file.script")
        val result = filter.applyFilter(line, line.length)

        assertThat(result).isNotNull()
        assertThat(result!!.resultItems).hasSize(1)

        val item = result.resultItems[0]
        assertThat(line.substring(item.highlightStartOffset, item.highlightEndOffset))
            .isEqualTo("deeply/nested/path/with-dashes/and_underscores/file.script:123")
    }

    @Test
    fun `calculates correct offset positions when line is part of larger text`() {
        val previousText = "Some previous log messages\n"
        val line = "ERROR:SCRIPT: main/test.lua:4: error message"
        val entireLength = previousText.length + line.length

        createDefoldFile("main/test.lua")

        val result = filter.applyFilter(line, entireLength)

        assertThat(result).isNotNull()
        assertThat(result!!.resultItems).hasSize(1)

        val item = result.resultItems[0]
        val fullText = previousText + line
        assertThat(fullText.substring(item.highlightStartOffset, item.highlightEndOffset))
            .isEqualTo("main/test.lua:4")
    }

    @Test
    fun `regex pattern matches expected file references`() {
        val validPatterns = listOf(
            "main/test.script:10",
            "folder/subfolder/file.lua:1",
            "complex-path/with_underscores/file.gui_script:999",
            "render/shader.render_script:42",
            "editor/tool.editor_script:5"
        )

        validPatterns.forEach { pattern ->
            val line = "Error: $pattern some message"
            createDefoldFile(pattern.substringBefore(':'))

            val result = filter.applyFilter(line, line.length)

            assertThat(result)
                .withFailMessage("Should match pattern: $pattern")
                .isNotNull()
        }
    }
}
