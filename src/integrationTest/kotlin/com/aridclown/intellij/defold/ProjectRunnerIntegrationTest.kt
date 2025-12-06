package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.aridclown.intellij.defold.util.ResourceUtil
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.replaceService
import io.mockk.*
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
@TestFixtures
class ProjectRunnerIntegrationTest {
    private val projectPathFixture = tempPathFixture()
    private val projectFixture = projectFixture(projectPathFixture, openAfterCreation = true)

    private val console = mockk<ConsoleView>(relaxed = true)
    private val config = mockk<DefoldEditorConfig>(relaxed = true)
    private val engineDiscoveryService = mockk<EngineDiscoveryService>(relaxed = true)
    private val processHandler = mockk<OSProcessHandler>(relaxed = true)

    @BeforeEach
    fun setUp() {
        // Setup engine discovery service mock
        every { engineDiscoveryService.hasEngineForPort(any()) } returns false
        every { engineDiscoveryService.stopEnginesForPort(any()) } just Runs

        // Mock resource copying
        mockkObject(ResourceUtil)
        every { ResourceUtil.copyResourcesToProject(any(), any(), any(), any()) } just Runs

        // Mock external dependencies
        mockkConstructor(EngineExtractor::class)
        every { anyConstructed<EngineExtractor>().extractAndPrepareEngine(any(), any(), any()) } answers {
            Result.success(projectPathFixture.get().resolve("dmengine"))
        }

        mockkConstructor(ProjectBuilder::class)
        coEvery { anyConstructed<ProjectBuilder>().buildProject(any(), any()) } returns Result.success(Unit)

        mockkConstructor(EngineRunner::class)
        every { anyConstructed<EngineRunner>().launchEngine(any(), any()) } returns processHandler
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `executes full build and run pipeline`(): Unit = timeoutRunBlocking {
        val project = setupProjectWithGameFile()
        var callbackInvoked = false
        val request = createRunRequest(project, onEngineStarted = { callbackInvoked = true })

        ProjectRunner.run(request)
        delay(500)

        coVerify(exactly = 1) { anyConstructed<EngineExtractor>().extractAndPrepareEngine(project, config, any()) }
        coVerify(exactly = 1) { anyConstructed<ProjectBuilder>().buildProject(any(), any()) }
        verify(exactly = 1) { anyConstructed<EngineRunner>().launchEngine(any(), any()) }
        verify(exactly = 1) { ResourceUtil.copyResourcesToProject(project, any(), any(), any()) }
        assertThat(callbackInvoked).isTrue
    }

    @Test
    fun `stops engines before launch`(): Unit = timeoutRunBlocking {
        val project = setupProjectWithGameFile()
        ProjectRunner.run(createRunRequest(project, debugPort = 8888)).join()

        verify(exactly = 1) { engineDiscoveryService.stopEnginesForPort(8888) }
    }

    @Test
    fun `skips build when engine already running for debug session`(): Unit = timeoutRunBlocking {
        val project = setupProjectWithGameFile()
        every { engineDiscoveryService.hasEngineForPort(8888) } returns true

        ProjectRunner.run(createRunRequest(project, enableDebugScript = true, debugPort = 8888)).join()

        verify(exactly = 1) { engineDiscoveryService.hasEngineForPort(8888) }
        coVerify(exactly = 0) { anyConstructed<ProjectBuilder>().buildProject(any(), any()) }
    }

    @Test
    fun `injects debug script when enabled and build folder missing`(): Unit = timeoutRunBlocking {
        val rootDir = projectPathFixture.get()
        createGameProjectFile(rootDir, createDefaultGameProject())
        val project = projectFixture.get()
        replaceEngineDiscoveryService(project)

        ProjectRunner.run(createRunRequest(project, enableDebugScript = true)).join()

        val gameProjectFile = refreshVirtualFile(rootDir.resolve(GAME_PROJECT_FILE))
        val content = String(gameProjectFile.contentsToByteArray())

        // After cleanup, script should be removed
        assertThat(content).doesNotContain("debug_init_script")
    }

    @Test
    fun `removes debug script when disabled`(): Unit = timeoutRunBlocking {
        val rootDir = projectPathFixture.get()
        createGameProjectFile(rootDir, createGameProjectWithDebugScript())
        val project = projectFixture.get()
        replaceEngineDiscoveryService(project)

        ProjectRunner.run(createRunRequest(project, enableDebugScript = false)).join()

        val gameProjectFile = refreshVirtualFile(rootDir.resolve(GAME_PROJECT_FILE))
        val content = String(gameProjectFile.contentsToByteArray())
        assertThat(content).doesNotContain("debug_init_script")
    }

    @Test
    fun `handles missing game project gracefully`(): Unit = timeoutRunBlocking {
        val project = projectFixture.get()
        replaceEngineDiscoveryService(project)
        // Don't create game.project file

        ProjectRunner.run(createRunRequest(project, enableDebugScript = true)).join()

        verify(exactly = 1) { console.print(match { it.contains("Warning: Game project file not found") }, any()) }
        // Build should still proceed
        coVerify(exactly = 1) { anyConstructed<ProjectBuilder>().buildProject(any(), any()) }
    }

    @Test
    fun `reports engine extraction failure`(): Unit = timeoutRunBlocking {
        createGameProjectFile(projectPathFixture.get())
        val project = projectFixture.get()
        replaceEngineDiscoveryService(project)

        every { anyConstructed<EngineExtractor>().extractAndPrepareEngine(any(), any(), any()) } returns
            Result.failure(IllegalStateException("No engine"))

        ProjectRunner.run(createRunRequest(project)).join()

        verify(exactly = 1) { console.print(match { it.contains("Build failed: No engine") }, any()) }
        coVerify(exactly = 0) { anyConstructed<ProjectBuilder>().buildProject(any(), any()) }
    }

    @Test
    fun `reports build failure`(): Unit = timeoutRunBlocking {
        createGameProjectFile(projectPathFixture.get())
        val project = projectFixture.get()
        replaceEngineDiscoveryService(project)

        coEvery { anyConstructed<ProjectBuilder>().buildProject(any(), any()) } returns
            Result.failure(RuntimeException("Build failed with exit code 1"))

        ProjectRunner.run(createRunRequest(project)).join()

        verify(exactly = 0) { anyConstructed<EngineRunner>().launchEngine(any(), any()) }
    }

    @Test
    fun `reports non-build-exception failures`(): Unit = timeoutRunBlocking {
        createGameProjectFile(projectPathFixture.get())
        val project = projectFixture.get()
        replaceEngineDiscoveryService(project)

        coEvery { anyConstructed<ProjectBuilder>().buildProject(any(), any()) } returns
            Result.failure(RuntimeException("Custom error"))

        ProjectRunner.run(createRunRequest(project)).join()

        verify(exactly = 1) { console.print(match { it.contains("Build failed: Custom error") }, any()) }
    }

    @Test
    fun `handles game project parse errors gracefully`(): Unit = timeoutRunBlocking {
        val rootDir = projectPathFixture.get()
        createGameProjectFile(rootDir, "invalid [[ INI")
        val project = projectFixture.get()
        replaceEngineDiscoveryService(project)

        ProjectRunner.run(createRunRequest(project, enableDebugScript = true)).join()

        verify(atLeast = 1) { console.print(match { it.contains("Failed to update game.project") }, any()) }
        // Should still proceed with build
        coVerify(exactly = 1) { anyConstructed<ProjectBuilder>().buildProject(any(), any()) }
    }

    @Test
    fun `passes custom build commands to builder`(): Unit = timeoutRunBlocking {
        createGameProjectFile(projectPathFixture.get())
        val project = projectFixture.get()
        replaceEngineDiscoveryService(project)

        val customCommands = listOf("clean", "build", "bundle")
        ProjectRunner.run(createRunRequest(project, buildCommands = customCommands)).join()

        coVerify(exactly = 1) {
            anyConstructed<ProjectBuilder>().buildProject(
                match { it.commands == customCommands },
                any()
            )
        }
    }

    @Test
    fun `passes environment variables to extraction and build`(): Unit = timeoutRunBlocking {
        createGameProjectFile(projectPathFixture.get())
        val project = projectFixture.get()
        replaceEngineDiscoveryService(project)

        val envData = EnvironmentVariablesData.create(mapOf("FOO" to "bar"), true)
        ProjectRunner.run(createRunRequest(project, envData = envData)).join()

        verify(exactly = 1) {
            anyConstructed<EngineExtractor>().extractAndPrepareEngine(
                project,
                config,
                envData
            )
        }
        coVerify(exactly = 1) {
            anyConstructed<ProjectBuilder>().buildProject(
                match { it.envData == envData },
                any()
            )
        }
    }

    private fun setupProjectWithGameFile(content: String = createDefaultGameProject()): Project {
        val rootDir = projectPathFixture.get()
        createGameProjectFile(rootDir, content)
        refreshVirtualFile(rootDir.resolve(GAME_PROJECT_FILE))

        return projectFixture.get().also(::replaceEngineDiscoveryService)
    }

    private fun createDefaultGameProject() =
        """
        [project]
        title = Test Project

        [bootstrap]
        main_collection = /main/main.collectionc
        """.trimIndent()

    private fun createGameProjectWithDebugScript() =
        """
        [project]
        title = Test Project

        [bootstrap]
        main_collection = /main/main.collectionc
        debug_init_script = /debugger/mobdebug_init.luac
        """.trimIndent()

    private fun createGameProjectFile(
        projectDir: Path,
        content: String = createDefaultGameProject()
    ) {
        val gameProjectPath = projectDir.resolve(GAME_PROJECT_FILE)
        Files.writeString(gameProjectPath, content)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(gameProjectPath)
    }

    private fun refreshVirtualFile(path: Path): VirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
        ?: error("Virtual file not found for path: $path")

    private fun replaceEngineDiscoveryService(project: Project) {
        project.replaceService(
            EngineDiscoveryService::class.java,
            engineDiscoveryService,
            project
        )
    }

    private fun createRunRequest(
        project: Project,
        enableDebugScript: Boolean = false,
        debugPort: Int? = null,
        buildCommands: List<String> = listOf("build"),
        envData: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
        onEngineStarted: (OSProcessHandler) -> Unit = {}
    ) = RunRequest(
        project = project,
        config = config,
        console = console,
        enableDebugScript = enableDebugScript,
        serverPort = null,
        debugPort = debugPort,
        envData = envData,
        buildCommands = buildCommands,
        onEngineStarted = onEngineStarted
    )
}
