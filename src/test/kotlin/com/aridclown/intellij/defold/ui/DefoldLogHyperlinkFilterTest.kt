package com.aridclown.intellij.defold.ui

import com.intellij.execution.filters.Filter.Result
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

    private val mockProject = mockk<Project>()
    private val filter = DefoldLogHyperlinkFilter(mockProject)

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
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
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(LocalFileSystem::class)
    }

    @Test
    fun `handles simple script file with line number`() {
        val line = "ERROR:SCRIPT: main/abc/def/test.script:17: attempt to index global 'asd' (a nil value)"
        val entireLength = line.length

        createDefoldFile("main/abc/def/test.script")

        val item = expectSingleMatch(line, "main/abc/def/test.script:17", entireLength)

        val hyperlink = item.hyperlinkInfo as OpenFileHyperlinkInfo
        assertThat(hyperlink).isNotNull()
    }

    @Test
    fun `handles lua file with line number`() {
        val line = "ERROR:SCRIPT: main/test.lua:4: attempt to index global 'asd' (a nil value)"
        val entireLength = line.length

        createDefoldFile("main/test.lua")

        expectSingleMatch(line, "main/test.lua:4", entireLength)
    }

    @Test
    fun `handles multiple file references in same line`() {
        val line = "  main/abc/def/test.script:24: in function <main/abc/def/test.script:16>"
        val entireLength = line.length

        createDefoldFile("main/abc/def/test.script")

        expectMatches(
            line,
            listOf("main/abc/def/test.script:24", "main/abc/def/test.script:16"),
            entireLength
        )
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
        expectMatches(errorLine, listOf("main/test.lua:4"))

        val traceLine1 = lines[2]
        expectMatches(traceLine1, listOf("main/test.lua:4"))

        val traceLine2 = lines[3]
        expectMatches(traceLine2, listOf("def/test.script:20"))

        val traceLine3 = lines[4]
        expectMatches(
            traceLine3,
            listOf("def/test.script:117", "def/test.script:115")
        )
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
            expectSingleMatch(line, fileRef)
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
            assertNoMatch(line)
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
            assertNoMatch(line)
        }
    }

    @Test
    fun `handles complex file paths`() {
        val line = "ERROR: deeply/nested/path/with-dashes/and_underscores/file.script:123: error"
        createDefoldFile("deeply/nested/path/with-dashes/and_underscores/file.script")
        expectSingleMatch(line, "deeply/nested/path/with-dashes/and_underscores/file.script:123")
    }

    @Test
    fun `calculates correct offset positions when line is part of larger text`() {
        val previousText = "Some previous log messages\n"
        val line = "ERROR:SCRIPT: main/test.lua:4: error message"
        val entireLength = previousText.length + line.length

        createDefoldFile("main/test.lua")

        val fullText = previousText + line
        expectSingleMatch(line, "main/test.lua:4", entireLength, fullText)
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
            expectMatches(line, listOf(pattern))
        }
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

    private fun expectMatches(
        line: String,
        expectedHighlights: List<String>,
        entireLength: Int = line.length,
        fullText: String = line
    ): Result {
        val result = filter.applyFilter(line, entireLength)

        assertThat(result).isNotNull()

        val items = result!!.resultItems
        assertThat(items).hasSize(expectedHighlights.size)

        expectedHighlights.forEachIndexed { index, expected ->
            val item = items[index]
            val actualText = fullText.substring(item.highlightStartOffset, item.highlightEndOffset)
            assertThat(actualText).isEqualTo(expected)
        }

        return result
    }

    private fun expectSingleMatch(
        line: String,
        expectedHighlight: String,
        entireLength: Int = line.length,
        fullText: String = line
    ) = expectMatches(line, listOf(expectedHighlight), entireLength, fullText).resultItems.single()

    private fun assertNoMatch(line: String, entireLength: Int = line.length) {
        val result = filter.applyFilter(line, entireLength)
        assertThat(result).isNull()
    }
}
