package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldVersion
import com.aridclown.intellij.defold.util.NotificationService
import com.aridclown.intellij.defold.util.NotificationService.notifyInfo
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
    private val defoldVersion = "1.6.5"
    private lateinit var manager: LuarcConfigurationManager

    @TempDir
    private lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        mockkStatic(LocalFileSystem::getInstance)
        mockkObject(NotificationService)
        every { project.defoldVersion } returns defoldVersion
        every { LocalFileSystem.getInstance() } returns fileSystem
        every { any<Project>().notifyInfo(any(), any()) } just Runs

        manager = LuarcConfigurationManager()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
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
            ".lua",
            ".script",
            ".gui_script",
            ".render_script",
            ".editor_script"
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

        clearMocks(NotificationService, project, answers = false)

        manager.ensureConfiguration(project, apiDir)

        val luarcFile = projectRoot.resolve(".luarc.json")
        assertThat(Files.exists(luarcFile)).isTrue

        // Verify it's valid JSON
        assertDoesNotThrow {
            val content = Files.readString(luarcFile)
            JSONObject(content)
        }

        verify {
            project.notifyInfo(
                "Defold annotations ready",
                match { it.contains("Defold API $defoldVersion") }
            )
        }
    }

    @Test
    fun `uses latest label when version is missing`() {
        val projectRoot = tempDir
        val apiDir = tempDir.resolve("defold_api")
        Files.createDirectories(apiDir)

        every { project.defoldVersion } returns "  "
        every { project.basePath } returns projectRoot.toString()

        clearMocks(NotificationService, project, answers = false)

        manager.ensureConfiguration(project, apiDir)

        verify {
            project.notifyInfo(
                "Defold annotations ready",
                match { it.contains("Defold API latest") }
            )
        }
    }

    @Test
    fun `adds LuaLS entries to existing configuration`() {
        val projectRoot = tempDir
        val apiDir = tempDir.resolve("defold_api")
        val luarcFile = projectRoot.resolve(".luarc.json")

        Files.createDirectories(apiDir)
        Files.writeString(
            luarcFile,
            """
                {
                    "existing": "config",
                    "workspace": {
                        "library": ["/custom/path"]
                    },
                    "runtime": {}
                }
            """.trimIndent()
        )

        every { project.basePath } returns projectRoot.toString()

        clearMocks(NotificationService, project, answers = false)

        manager.ensureConfiguration(project, apiDir)

        val updatedJson = JSONObject(Files.readString(luarcFile))
        val workspace = updatedJson.getJSONObject("workspace")
        val libraries = workspace.getJSONArray("library").toList()
        val extensions = updatedJson.getJSONObject("runtime").getJSONArray("extensions").toList()

        assertThat(updatedJson.getString("existing")).isEqualTo("config")
        assertThat(libraries).contains("/custom/path")
        assertThat(libraries.any { it.toString().contains("defold_api") }).isTrue
        assertThat(extensions).containsExactlyInAnyOrder(
            ".lua",
            ".script",
            ".gui_script",
            ".render_script",
            ".editor_script"
        )

        verify {
            project.notifyInfo(
                "Defold annotations ready",
                match { it.contains("Defold API $defoldVersion") }
            )
        }
    }

    @Test
    fun `does not rewrite configuration when entries already exist`() {
        val projectRoot = tempDir
        val apiDir = tempDir.resolve("defold_api")
        Files.createDirectories(apiDir)

        every { project.basePath } returns projectRoot.toString()

        manager.ensureConfiguration(project, apiDir)

        val luarcFile = projectRoot.resolve(".luarc.json")
        val originalContent = Files.readString(luarcFile)

        clearMocks(NotificationService, project, answers = false)

        manager.ensureConfiguration(project, apiDir)

        assertThat(Files.readString(luarcFile)).isEqualTo(originalContent)

        verify(exactly = 0) { project.notifyInfo(any(), any()) }
    }

    @Test
    fun `moves schema entry to the top of existing configuration`() {
        val projectRoot = tempDir
        val apiDir = tempDir.resolve("defold_api")
        Files.createDirectories(apiDir)

        val luarcFile = projectRoot.resolve(".luarc.json")
        Files.writeString(
            luarcFile,
            $$"""
                {
                    "workspace": {
                        "library": [ "$${apiDir.toAbsolutePath()}" ],
                        "checkThirdParty": false,
                        "ignoreDir": [ "debugger" ]
                    },
                    "runtime": {
                        "version": "Lua 5.1",
                        "extensions": [
                            ".lua",
                            ".script",
                            ".gui_script",
                            ".render_script",
                            ".editor_script"
                        ]
                    },
                    "$schema": "https://raw.githubusercontent.com/LuaLS/vscode-lua/master/setting/schema.json"
                }
            """.trimIndent()
        )

        every { project.basePath } returns projectRoot.toString()

        clearMocks(NotificationService, project, answers = false)

        manager.ensureConfiguration(project, apiDir)

        val content = Files.readString(luarcFile)
        val schemaIndex = content.indexOf($$"\"$schema\"")
        val workspaceIndex = content.indexOf("\"workspace\"")
        val runtimeIndex = content.indexOf("\"runtime\"")

        assertThat(schemaIndex).isGreaterThanOrEqualTo(0)
        assertThat(workspaceIndex).isGreaterThan(schemaIndex)
        assertThat(runtimeIndex).isGreaterThan(schemaIndex)
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

        clearMocks(NotificationService, project, answers = false)

        // Should not throw, just log warning
        assertDoesNotThrow {
            manager.ensureConfiguration(project, apiDir)
        }

        verify(exactly = 0) { project.notifyInfo(any(), any()) }
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
