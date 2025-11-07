package com.aridclown.intellij.defold

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LuarcConfigurationManagerTest {

    private val project = mockk<Project>(relaxed = true)
    private val fileSystem = mockk<LocalFileSystem>(relaxed = true)
    private lateinit var manager: LuarcConfigurationManager

    @TempDir
    private lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        mockkStatic(LocalFileSystem::getInstance)
        every { LocalFileSystem.getInstance() } returns fileSystem

        manager = LuarcConfigurationManager()
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(LocalFileSystem::getInstance)
        clearAllMocks()
    }

    @Test
    fun `generates valid JSON configuration`() {
        val apiPath = "/path/to/defold_api"

        val content = manager.generateContent(apiPath)

        val json = JSONObject(content)
        val extensions = json.getJSONObject("runtime").getJSONArray("extensions")

        assertThat(json.has($$"$schema")).isTrue
        assertThat(json.getJSONObject("workspace").getJSONArray("library").length()).isEqualTo(1)
        assertThat(json.getJSONObject("workspace").getJSONArray("library").getString(0))
            .isEqualTo("/path/to/defold_api")

        val ignoreDir = json.getJSONObject("workspace").getJSONArray("ignoreDir")
        assertThat(ignoreDir.length()).isEqualTo(1)
        assertThat(ignoreDir.getString(0)).isEqualTo("debugger")

        assertThat(json.getJSONObject("runtime").getString("version")).isEqualTo("Lua 5.1")
        assertThat(json.getJSONObject("workspace").getBoolean("checkThirdParty")).isFalse

        assertThat(extensions.length()).isEqualTo(5)
        assertThat(extensions.toList()).containsExactlyInAnyOrder(
            ".lua", ".script", ".gui_script", ".render_script", ".editor_script"
        )
    }

    @Test
    fun `normalizes library path in configuration`() {
        val apiPath = "/path/with/../normalized/api"

        val content = manager.generateContent(apiPath)

        val json = JSONObject(content)
        val libraryPath = json.getJSONObject("workspace").getJSONArray("library").getString(0)

        // Path should be normalized (../normalized removed)
        assertThat(libraryPath).isEqualTo("/path/normalized/api")
    }

    @Test
    fun `creates configuration file when it does not exist`() {
        val projectRoot = tempDir
        val apiDir = tempDir.resolve("defold_api")
        Files.createDirectories(apiDir)

        every { project.basePath } returns projectRoot.toString()

        manager.ensureConfiguration(project, apiDir)

        val luarcFile = projectRoot.resolve(".luarc.json")
        assertThat(Files.exists(luarcFile)).isTrue

        // Verify it's valid JSON
        assertDoesNotThrow {
            val content = Files.readString(luarcFile)
            JSONObject(content)
        }
    }

    @Test
    fun `skips creation when configuration file already exists`() {
        val projectRoot = tempDir
        val apiDir = tempDir.resolve("defold_api")
        val luarcFile = projectRoot.resolve(".luarc.json")

        Files.createDirectories(apiDir)
        Files.writeString(luarcFile, """{"existing": "config"}""")
        val originalContent = Files.readString(luarcFile)

        every { project.basePath } returns projectRoot.toString()

        manager.ensureConfiguration(project, apiDir)

        // File should not be modified
        assertThat(Files.readString(luarcFile)).isEqualTo(originalContent)
    }

    @Test
    fun `creates parent directories if needed`() {
        val projectRoot = tempDir.resolve("project/subdir")
        val apiDir = tempDir.resolve("defold_api")
        Files.createDirectories(apiDir)

        every { project.basePath } returns projectRoot.toString()

        manager.ensureConfiguration(project, apiDir)

        val luarcFile = projectRoot.resolve(".luarc.json")
        assertThat(Files.exists(luarcFile)).isTrue
        assertThat(Files.exists(projectRoot)).isTrue
    }

    @Test
    fun `refreshes virtual file system after creating configuration`() {
        val projectRoot = tempDir
        val apiDir = tempDir.resolve("defold_api")
        Files.createDirectories(apiDir)

        every { project.basePath } returns projectRoot.toString()

        manager.ensureConfiguration(project, apiDir)

        verify { fileSystem.refreshNioFiles(match<List<Path>> { it.size == 1 }) }
    }

    @Test
    fun `handles file write errors gracefully`() {
        val projectRoot = tempDir
        val apiDir = tempDir.resolve("defold_api")
        Files.createDirectories(apiDir)

        // Create .luarc.json as a directory to cause write error
        val luarcFile = projectRoot.resolve(".luarc.json")
        Files.createDirectories(luarcFile)

        every { project.basePath } returns projectRoot.toString()

        // Should not throw, just log warning
        assertDoesNotThrow {
            manager.ensureConfiguration(project, apiDir)
        }
    }

    @Test
    fun `generates configuration with absolute path`() {
        val apiDir = tempDir.resolve("defold_api")
        Files.createDirectories(apiDir)

        val content = manager.generateContent(apiDir.toAbsolutePath().toString())

        val json = JSONObject(content)
        val libraryPath = json.getJSONObject("workspace").getJSONArray("library").getString(0)

        assertThat(libraryPath).startsWith("/")
        assertThat(libraryPath).contains("defold_api")
    }
}
