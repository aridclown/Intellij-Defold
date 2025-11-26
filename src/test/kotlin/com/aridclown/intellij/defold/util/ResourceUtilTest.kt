package com.aridclown.intellij.defold.util

import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ResourceUtilTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `text resources can be loaded from plugin jar`() {
        val content =
            ResourceUtil.loadTextResource(
                "testdata/util/sample.txt",
                javaClass.classLoader
            )
        assertThat(content).isEqualTo("Hello from test resource!\n")
    }

    @Test
    fun `loading missing resource throws with clear error message`() {
        assertThatThrownBy {
            ResourceUtil.loadTextResource("missing.txt", javaClass.classLoader)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Could not load missing.txt resource")
    }

    @Test
    fun `resources are copied to project directory when missing`() {
        val project =
            mockk<Project> {
                every { basePath } returns tempDir.toString()
            }
        val targetFile = tempDir.resolve("debugger/mobdebug.lua")

        ResourceUtil.copyResourcesToProject(project, javaClass.classLoader, "debugger/mobdebug.lua")

        assertThat(targetFile).exists()
        assertThat(Files.readString(targetFile)).contains("mobdebug")
    }

    @Test
    fun `existing project files are never overwritten`() {
        val project =
            mockk<Project> {
                every { basePath } returns tempDir.toString()
            }
        val targetFile = tempDir.resolve("debugger/mobdebug.lua")
        Files.createDirectories(targetFile.parent)
        Files.writeString(targetFile, "original content")

        ResourceUtil.copyResourcesToProject(project, javaClass.classLoader, "debugger/mobdebug.lua")

        assertThat(Files.readString(targetFile)).isEqualTo("original content")
    }

    @Test
    fun `projects without base path are handled gracefully`() {
        val project =
            mockk<Project> {
                every { basePath } returns null
            }

        assertThatCode {
            ResourceUtil.copyResourcesToProject(project, javaClass.classLoader, "debugger/mobdebug.lua")
        }.doesNotThrowAnyException()
    }

    @Test
    fun `lua scripts support placeholder replacement`() {
        val script =
            ResourceUtil.loadAndProcessLuaScript(
                resourcePath = "testdata/util/script.lua",
                compactWhitespace = false,
                "\${NAME}" to "World",
                "\${PLACEHOLDER}" to "test"
            )

        assertThat(script)
            .contains("Hello, World!")
            .contains("placeholder test")
            .doesNotContain("\${NAME}")
            .doesNotContain("\${PLACEHOLDER}")
    }

    @Test
    fun `lua scripts can be compacted to single line`() {
        val script =
            ResourceUtil.loadAndProcessLuaScript(
                resourcePath = "testdata/util/script.lua",
                compactWhitespace = true,
                "\${NAME}" to "World",
                "\${PLACEHOLDER}" to "test"
            )

        // Whitespace (including newlines) should be compacted to single spaces
        assertThat(script)
            .isEqualTo("print(\"Hello, World!\") -- placeholder test")
            .doesNotContain("\n")
    }

    @Test
    fun `lua scripts preserve formatting when compact disabled`() {
        val script =
            ResourceUtil.loadAndProcessLuaScript(
                resourcePath = "testdata/util/script.lua",
                compactWhitespace = false,
                "\${NAME}" to "World",
                "\${PLACEHOLDER}" to "test"
            )

        // Should preserve the newline between the two lines
        assertThat(script).contains("\n")
    }
}
