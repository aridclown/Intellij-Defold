package com.aridclown.intellij.defold.hotreload

import com.aridclown.intellij.defold.DefoldProjectService
import com.aridclown.intellij.defold.engine.DefoldEngineEndpoint
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.openapi.project.Project
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.absolutePathString

class DefoldHotReloadServiceTest {

    private val mockProject = mockk<Project>(relaxed = true)
    private val mockDefoldService = mockk<DefoldProjectService>(relaxed = true)
    private lateinit var hotReloadService: DefoldHotReloadService

    @TempDir
    lateinit var projectDir: Path

    @BeforeEach
    fun setUp() {
        every { mockProject.getService(DefoldProjectService::class.java) } returns mockDefoldService
        every { mockProject.basePath } returns projectDir.absolutePathString()

        hotReloadService = DefoldHotReloadService(mockProject)
    }

    @Test
    fun `performHotReload should abort when engine is unreachable`() {
        val recording = recordingConsole()
        val dependencies = mockk<HotReloadDependencies>()
        every { dependencies.obtainConsole() } returns recording.console
        every { dependencies.ensureReachableEngine(recording.console) } answers {
            recording.console.print(
                "[HotReload] Defold engine not reachable. Make sure the game is running from IntelliJ\n",
                ERROR_OUTPUT
            )
            null
        }

        hotReloadService.setDependenciesForTesting(dependencies)

        runBlocking { hotReloadService.performHotReload() }

        assertThat(recording.events.map { it.text })
            .anySatisfy { assertThat(it).contains("Defold engine not reachable") }
    }

    @Test
    fun `performHotReload should report build failure`() {
        val recording = recordingConsole()
        val dependencies = mockk<HotReloadDependencies>()
        val endpoint = DefoldEngineEndpoint("127.0.0.1", 9000, null, System.currentTimeMillis())
        every { dependencies.obtainConsole() } returns recording.console
        every { dependencies.ensureReachableEngine(recording.console) } returns endpoint
        coEvery { dependencies.buildProject(recording.console) } returns false

        hotReloadService.setDependenciesForTesting(dependencies)

        runBlocking { hotReloadService.performHotReload() }

        assertThat(recording.events.map { it.text })
            .anySatisfy { assertThat(it).contains("Build failed, cannot perform hot reload") }
    }

    @Test
    fun `performHotReload should skip reload when no resources changed`() {
        val recording = recordingConsole()

        hotReloadService.setArtifactsForTesting(
            mapOf(
                "/main/player.scriptc" to BuildArtifact(
                    "/main/player.scriptc",
                    "/default/main/player.scriptc",
                    "etag-old"
                )
            )
        )

        val dependencies = mockk<HotReloadDependencies>()
        val endpoint = DefoldEngineEndpoint("127.0.0.1", 9000, null, System.currentTimeMillis())
        every { dependencies.obtainConsole() } returns recording.console
        every { dependencies.ensureReachableEngine(recording.console) } returns endpoint
        coEvery { dependencies.buildProject(recording.console) } returns true

        hotReloadService.setDependenciesForTesting(dependencies)

        runBlocking { hotReloadService.performHotReload() }

        assertThat(recording.events.map { it.text })
            .anySatisfy { assertThat(it).contains("Defold build completed") }
        assertThat(recording.events.map { it.text })
            .noneMatch { it.contains("Reloaded") }
    }

    @Test
    fun `performHotReload should send reload payload for changed resources`() {
        val recording = recordingConsole()
        val endpoint = DefoldEngineEndpoint("127.0.0.1", 9000, null, System.currentTimeMillis())
        val compiledFile = projectDir
            .resolve("build")
            .resolve("default")
            .resolve("main")
            .resolve("player.scriptc")

        Files.createDirectories(compiledFile.parent)
        Files.writeString(compiledFile, "original")
        hotReloadService.refreshBuildArtifacts()

        var capturedPayload: ByteArray? = null

        val dependencies = mockk<HotReloadDependencies>()
        every { dependencies.obtainConsole() } returns recording.console
        every { dependencies.ensureReachableEngine(recording.console) } returns endpoint
        coEvery { dependencies.buildProject(recording.console) } coAnswers {
            Files.writeString(compiledFile, "updated")
            true
        }
        val payloadSlot = slot<ByteArray>()
        every { dependencies.sendResourceReload(endpoint, capture(payloadSlot)) } answers {
            capturedPayload = payloadSlot.captured
        }

        hotReloadService.setDependenciesForTesting(dependencies)

        runBlocking { hotReloadService.performHotReload() }

        assertThat(capturedPayload).isNotNull
        val decoded = decodeResourcePaths(capturedPayload!!)
        assertThat(decoded).containsExactly("/main/player.scriptc")
        assertThat(recording.events.map { it.text })
            .anySatisfy { assertThat(it).contains("Defold build completed") }
        assertThat(recording.events.map { it.text })
            .anySatisfy { assertThat(it).contains("Reloaded 1 resources") }
    }

    @Test
    fun `should calculate ETags correctly`() {
        val compiledFile = projectDir
            .resolve("build")
            .resolve("default")
            .resolve("main")
            .resolve("player.scriptc")

        val parent = compiledFile.parent ?: error("Unexpected null parent")
        Files.createDirectories(parent)
        Files.writeString(compiledFile, "test content")

        hotReloadService.refreshBuildArtifacts()

        val artifact = hotReloadService.currentArtifactsForTesting()["/main/player.scriptc"]
        val expected = calculateTestEtag("test content")

        assertThat(artifact).isNotNull
        assertThat(artifact!!.etag).isEqualTo(expected)
    }

    @Test
    fun `should detect changed resources by comparing ETags`() {
        hotReloadService.setArtifactsForTesting(
            mapOf(
                "/main/player.scriptc" to BuildArtifact("/main/player.scriptc", "/default/main/player.scriptc", "new"),
                "/textures/player.texturec" to BuildArtifact(
                    "/textures/player.texturec",
                    "/default/textures/player.texturec",
                    "new-tex"
                ),
                "/gui/menu.gui_scriptc" to BuildArtifact(
                    "/gui/menu.gui_scriptc",
                    "/default/gui/menu.gui_scriptc",
                    "new-gui"
                )
            )
        )

        val result = hotReloadService.findChangedArtifacts(
            mapOf(
                "/main/player.scriptc" to BuildArtifact("/main/player.scriptc", "/default/main/player.scriptc", "old"),
                "/textures/player.texturec" to BuildArtifact(
                    "/textures/player.texturec",
                    "/default/textures/player.texturec",
                    "old-tex"
                ),
                "/gui/menu.gui_scriptc" to BuildArtifact(
                    "/gui/menu.gui_scriptc",
                    "/default/gui/menu.gui_scriptc",
                    "old-gui"
                )
            )
        )

        assertThat(result.map(BuildArtifact::normalizedPath)).containsExactlyInAnyOrder(
            "/main/player.scriptc",
            "/gui/menu.gui_scriptc"
        )
    }

    @Test
    fun `should create correct resource reload protobuf message`() {
        val artifacts = listOf(
            BuildArtifact("/main/player.scriptc", "/default/main/player.scriptc", "etag-1"),
            BuildArtifact("/utils/helper.lua", "/default/utils/helper.lua", "etag-2")
        )

        val payload = hotReloadService.createProtobufReloadPayload(artifacts)
        val decodedPaths = decodeResourcePaths(payload)

        assertThat(decodedPaths).containsExactly(
            "/main/player.scriptc",
            "/utils/helper.lua"
        )
    }

    @Test
    fun `should filter resources by supported extensions`() {
        hotReloadService.setArtifactsForTesting(
            mapOf(
                "/main/player.scriptc" to BuildArtifact("/main/player.scriptc", "", "etag-1"),
                "/utils/helper.lua" to BuildArtifact("/utils/helper.lua", "", "etag-2"),
                "/gui/menu.gui_scriptc" to BuildArtifact("/gui/menu.gui_scriptc", "", "etag-3"),
                "/objects/enemy.goc" to BuildArtifact("/objects/enemy.goc", "", "etag-4"),
                "/images/player.texturec" to BuildArtifact("/images/player.texturec", "", "etag-5")
            )
        )

        val result = hotReloadService.findChangedArtifacts(
            mapOf(
                "/main/player.scriptc" to BuildArtifact("/main/player.scriptc", "", "old-1"),
                "/utils/helper.lua" to BuildArtifact("/utils/helper.lua", "", "old-2"),
                "/gui/menu.gui_scriptc" to BuildArtifact("/gui/menu.gui_scriptc", "", "old-3"),
                "/objects/enemy.goc" to BuildArtifact("/objects/enemy.goc", "", "old-4"),
                "/images/player.texturec" to BuildArtifact("/images/player.texturec", "", "old-5")
            )
        )

        assertThat(result.map { it.normalizedPath }).containsExactlyInAnyOrder(
            "/main/player.scriptc",
            "/utils/helper.lua",
            "/gui/menu.gui_scriptc",
            "/objects/enemy.goc"
        )
    }

    @Test
    fun `should normalize compiled paths relative to build outputs`() {
        val samples = listOf(
            "/default/stars/factory.scriptc",
            "/x86_64-osx/default/stars/factory.scriptc",
            "/assets/tiles/tilemap.gui_scriptc"
        )

        val normalized = samples.associateWith { sample ->
            hotReloadService.normalizeCompiledPath(sample)
        }

        assertThat(normalized)
            .containsEntry(
                "/default/stars/factory.scriptc",
                "/stars/factory.scriptc"
            )
            .containsEntry(
                "/x86_64-osx/default/stars/factory.scriptc",
                "/x86_64-osx/default/stars/factory.scriptc"
            )
            .containsEntry(
                "/assets/tiles/tilemap.gui_scriptc",
                "/assets/tiles/tilemap.gui_scriptc"
            )
    }

    private fun calculateTestEtag(content: String): String {
        val bytes = content.toByteArray()
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun decodeResourcePaths(payload: ByteArray): List<String> {
        val paths = mutableListOf<String>()
        var cursor = 0
        while (cursor < payload.size) {
            cursor = expectFieldTag(payload, cursor, expectedTag = 0x0A)
            val (length, nextIndex) = readVarint(payload, cursor)
            cursor = nextIndex
            val pathBytes = payload.copyOfRange(cursor, cursor + length)
            paths += String(pathBytes, Charsets.UTF_8)
            cursor += length
        }
        return paths
    }

    private fun expectFieldTag(data: ByteArray, index: Int, expectedTag: Int): Int {
        val actual = data[index].toInt() and 0xFF
        assertThat(actual).isEqualTo(expectedTag)
        return index + 1
    }

    private fun readVarint(data: ByteArray, startIndex: Int): Pair<Int, Int> {
        var value = 0
        var shift = 0
        var index = startIndex
        while (true) {
            val byte = data[index].toInt() and 0xFF
            index++
            value = value or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
        }
        return value to index
    }

    private fun recordingConsole(): RecordingConsole {
        val events = mutableListOf<ConsoleEvent>()
        val console = mockk<ConsoleView>(relaxed = true)
        every { console.print(any(), any()) } answers {
            val text = args[0] as String
            val type = args[1] as ConsoleViewContentType
            events += ConsoleEvent(text, type)
        }
        return RecordingConsole(console, events)
    }

    private data class RecordingConsole(
        val console: ConsoleView,
        val events: MutableList<ConsoleEvent>
    )

    private data class ConsoleEvent(val text: String, val type: ConsoleViewContentType)
}
