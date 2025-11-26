package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.DEFAULT_MOBDEBUG_PORT
import com.aridclown.intellij.defold.EngineDiscoveryService.Companion.getEngineDiscoveryService
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.openapi.project.Project
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

class EngineRunnerTest {
    private val project = mockk<Project>(relaxed = true)
    private val console = mockk<ConsoleView>(relaxed = true)
    private val executor = mockk<ProcessExecutor>()
    private val engineDiscovery = mockk<EngineDiscoveryService>(relaxed = true)
    private val handler = mockk<OSProcessHandler>(relaxed = true)
    private val engineRunner = EngineRunner(executor)

    private val config =
        DefoldEditorConfig(
            version = "1.0",
            editorJar = "editor.jar",
            javaBin = "java",
            jarBin = "jar",
            launchConfig =
            LaunchConfigs.Config(
                buildPlatform = "x86",
                libexecBinPath = "bin",
                executable = "dmengine"
            )
        )

    @BeforeEach
    fun setUp() {
        mockkObject(EngineDiscoveryService.Companion)
        every { project.getEngineDiscoveryService() } returns engineDiscovery
        every { project.basePath } returns WORKSPACE
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkObject(EngineDiscoveryService.Companion)
    }

    @Test
    fun `uses project workspace and applies environment when starting engine`() {
        val request = runRequest(enableDebugScript = false)
        val command = captureCommand(request)

        assertThat(command.exePath).isEqualTo(ENGINE_PATH.toAbsolutePath().toString())
        assertThat(command.workDirectory!!.toPath()).isEqualTo(Path.of(WORKSPACE))
        assertThat(command.environment)
            .containsEntry("FOO", "BAR")
            .doesNotContainKeys("DM_SERVICE_PORT", "MOBDEBUG_PORT")
        assertThat(command.parametersList.parametersCount).isZero()
        verify(exactly = 1) { engineDiscovery.attachToProcess(handler, null) }
    }

    @Test
    fun `adds debug parameters and ports when debug script is enabled`() {
        val request = runRequest(enableDebugScript = true, serverPort = 8123, debugPort = 4500)
        val command = captureCommand(request)

        assertThat(command.parametersList.list)
            .contains("--config=bootstrap.debug_init_script=/debugger/mobdebug_init.luac")
        assertThat(command.environment)
            .containsEntry("FOO", "BAR")
            .containsEntry("DM_SERVICE_PORT", "8123")
            .containsEntry("MOBDEBUG_PORT", "4500")
        verify(exactly = 1) { engineDiscovery.attachToProcess(handler, 4500) }
    }

    @Test
    fun `falls back to default ports when debug configuration is incomplete`() {
        val request = runRequest(enableDebugScript = true, serverPort = null, debugPort = null)
        val command = captureCommand(request)

        assertThat(command.environment)
            .containsEntry("DM_SERVICE_PORT", "8001")
            .containsEntry("MOBDEBUG_PORT", DEFAULT_MOBDEBUG_PORT.toString())
        verify(exactly = 1) { engineDiscovery.attachToProcess(handler, null) }
    }

    @Test
    fun `reports failure when workspace path is missing`() {
        every { project.basePath } returns null

        val request = runRequest(enableDebugScript = false)
        val result = engineRunner.launchEngine(request, ENGINE_PATH)

        assertThat(result).isNull()
        verify { console.print("Failed to launch dmengine: Project has no base path\n", ERROR_OUTPUT) }
        verify(exactly = 0) { executor.execute(any()) }
        verify(exactly = 0) { engineDiscovery.attachToProcess(any(), any()) }
    }

    @Test
    fun `reports executor failure to console`() {
        val error = IllegalStateException("boom")
        every { executor.execute(any()) } throws error

        val request = runRequest(enableDebugScript = false)
        val result = engineRunner.launchEngine(request, ENGINE_PATH)

        assertThat(result).isNull()
        verify { console.print("Failed to launch dmengine: ${error.message}\n", ERROR_OUTPUT) }
        verify(exactly = 0) { engineDiscovery.attachToProcess(any(), any()) }
    }

    private fun runRequest(
        enableDebugScript: Boolean,
        serverPort: Int? = null,
        debugPort: Int? = null
    ): RunRequest = RunRequest(
        project = project,
        config = config,
        console = console,
        enableDebugScript = enableDebugScript,
        serverPort = serverPort,
        debugPort = debugPort,
        envData = EnvironmentVariablesData.create(mapOf("FOO" to "BAR"), true)
    )

    private fun captureCommand(request: RunRequest): GeneralCommandLine {
        val commandSlot = slot<GeneralCommandLine>()
        every { executor.execute(capture(commandSlot)) } returns handler

        val result = engineRunner.launchEngine(request, ENGINE_PATH)

        assertThat(result).isEqualTo(handler)
        return commandSlot.captured
    }

    private companion object {
        private const val WORKSPACE = "/workspace"
        private val ENGINE_PATH = Path.of("/tmp/dmengine")
    }
}
