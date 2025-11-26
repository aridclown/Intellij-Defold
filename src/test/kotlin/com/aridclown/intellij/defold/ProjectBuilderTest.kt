package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldProjectService.Companion.rootProjectFolder
import com.aridclown.intellij.defold.process.BackgroundProcessRequest
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class ProjectBuilderTest {
    private val project = mockk<Project>(relaxed = true)
    private val console = mockk<ConsoleView>(relaxed = true)
    private val processExecutor = mockk<ProcessExecutor>(relaxed = true)
    private val config = mockk<DefoldEditorConfig>(relaxed = true)
    private val projectFolder = mockk<VirtualFile>(relaxed = true)
    private val job = mockk<Job>(relaxed = true)

    private lateinit var builder: ProjectBuilder

    @BeforeEach
    fun setUp() {
        builder = ProjectBuilder(processExecutor)

        every { processExecutor.console } returns console
        every { config.editorJar } returns "/path/to/bob.jar"
        every { config.javaBin } returns "java"
        every { projectFolder.path } returns "/project/path"

        // Mock DefoldProjectService extension
        mockkStatic(DefoldProjectService::class)
        every { project.rootProjectFolder } returns projectFolder

        // Default: background process succeeds
        every { processExecutor.executeInBackground(any()) } returns job
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    // Helper functions

    private fun buildRequest(
        commands: List<String> = listOf("build"),
        envData: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
        onSuccess: () -> Unit = {},
        onFailure: (Int) -> Unit = {}
    ) = BuildRequest(
        project = project,
        config = config,
        commands = commands,
        envData = envData,
        onSuccess = onSuccess,
        onFailure = onFailure
    )

    private fun captureBackgroundRequest(): BackgroundProcessRequest {
        val slot = slot<BackgroundProcessRequest>()
        verify { processExecutor.executeInBackground(capture(slot)) }
        return slot.captured
    }

    private fun simulateSuccessfulBuild() {
        every { processExecutor.executeInBackground(any()) } answers {
            val bgRequest = firstArg<BackgroundProcessRequest>()
            bgRequest.onSuccess()
            job
        }
    }

    private fun simulateFailedBuild(exitCode: Int) {
        every { processExecutor.executeInBackground(any()) } answers {
            val bgRequest = firstArg<BackgroundProcessRequest>()
            bgRequest.onFailure(exitCode)
            job
        }
    }

    @Nested
    inner class BuildExecution {
        @Test
        fun `succeeds with valid project`(): Unit = runBlocking {
            val request = buildRequest()
            simulateSuccessfulBuild()

            val result = builder.buildProject(request)

            assertThat(result.isSuccess).isTrue
        }

        @Test
        fun `fails when project has no base path`(): Unit = runBlocking {
            every { project.rootProjectFolder } returns null
            val request = buildRequest()

            val result = builder.buildProject(request)

            assertThat(result.isFailure).isTrue
            assertThat(result.exceptionOrNull())
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("This is not a valid Defold project")
        }

        @Test
        fun `constructs correct Bob command line`(): Unit = runBlocking {
            val request = buildRequest(commands = listOf("build", "bundle"))
            simulateSuccessfulBuild()

            builder.buildProject(request)

            val bgRequest = captureBackgroundRequest()
            assertThat(bgRequest.command)
                .extracting(
                    { it.exePath },
                    { it.parametersList.parameters },
                    { it.workingDirectory }
                ).containsExactly(
                    "java",
                    listOf(
                        "-cp",
                        "/path/to/bob.jar",
                        "com.dynamo.bob.Bob",
                        "--variant=debug",
                        "build",
                        "bundle"
                    ),
                    Path("/project/path")
                )
        }

        @Test
        fun `applies environment variables to command`(): Unit = runBlocking {
            val envData = EnvironmentVariablesData.create(mapOf("FOO" to "bar", "BAZ" to "qux"), true)
            val request = buildRequest(envData = envData)
            simulateSuccessfulBuild()

            builder.buildProject(request)

            val bgRequest = captureBackgroundRequest()
            assertThat(bgRequest.command.environment).containsEntry("FOO", "bar")
            assertThat(bgRequest.command.environment).containsEntry("BAZ", "qux")
        }

        @Test
        fun `uses custom build commands`(): Unit = runBlocking {
            val request = buildRequest(commands = listOf("resolve", "distclean"))
            simulateSuccessfulBuild()

            builder.buildProject(request)

            val bgRequest = captureBackgroundRequest()
            assertThat(bgRequest.command.parametersList.parameters).contains("resolve", "distclean")
        }

        @Test
        fun `uses default build command when not specified`(): Unit = runBlocking {
            val request = buildRequest()
            simulateSuccessfulBuild()

            builder.buildProject(request)

            val bgRequest = captureBackgroundRequest()
            assertThat(bgRequest.command.parametersList.parameters).contains("build")
        }
    }

    @Nested
    inner class BuildProcessManagement {
        @Test
        fun `waits for process to finish`(): Unit = runBlocking {
            val request = buildRequest()
            simulateSuccessfulBuild()

            builder.buildProject(request)

            verify(exactly = 1) { processExecutor.executeInBackground(any()) }
        }

        @Test
        fun `captures console output through ProcessExecutor`(): Unit = runBlocking {
            val request = buildRequest()
            simulateSuccessfulBuild()

            builder.buildProject(request)

            val bgRequest = captureBackgroundRequest()
            assertThat(bgRequest.title).isEqualTo("Building Defold project")
            assertThat(bgRequest.project).isEqualTo(project)
        }

        @Test
        fun `detects non-zero exit code`(): Unit = runBlocking {
            val request = buildRequest()
            simulateFailedBuild(exitCode = 1)

            val result = builder.buildProject(request)

            assertThat(result.isFailure).isTrue
            assertThat(result.exceptionOrNull())
                .isInstanceOf(BuildProcessFailedException::class.java)
                .hasMessage("Bob build failed (exit code 1)")
        }
    }

    @Nested
    inner class CallbackHandling {
        @Test
        fun `should handle correct callback on success`(): Unit = runBlocking {
            var successCalled = false
            val request = buildRequest(onSuccess = { successCalled = true })
            simulateSuccessfulBuild()

            val result = builder.buildProject(request)

            assertThat(result.isSuccess).isTrue
            assertThat(successCalled).isTrue
        }

        @Test
        fun `should handle correct callback on failure`(): Unit = runBlocking {
            var failureCalled = false
            var capturedExitCode = -1
            val request =
                buildRequest(
                    onFailure = { exitCode ->
                        failureCalled = true
                        capturedExitCode = exitCode
                    }
                )
            simulateFailedBuild(exitCode = 42)

            val result = builder.buildProject(request)

            assertThat(result.isFailure).isTrue
            assertThat(result.exceptionOrNull())
                .isNotNull
                .isInstanceOf(BuildProcessFailedException::class.java)
                .hasMessage("Bob build failed (exit code 42)")
            assertThat(failureCalled).isTrue
            assertThat(capturedExitCode).isEqualTo(42)
        }
    }

    @Nested
    inner class CommandConstruction {
        @Test
        fun `includes Bob JAR path and debug variant flag`(): Unit = runBlocking {
            every { config.editorJar } returns "/custom/bob.jar"
            val request = buildRequest()
            simulateSuccessfulBuild()

            builder.buildProject(request)

            val bgRequest = captureBackgroundRequest()
            assertThat(bgRequest.command.parametersList.parameters)
                .contains("/custom/bob.jar")
                .contains("--variant=debug")
        }

        @Test
        fun `includes project folder as working directory`(): Unit = runBlocking {
            every { projectFolder.path } returns "/my/project"
            val request = buildRequest()
            simulateSuccessfulBuild()

            builder.buildProject(request)

            val bgRequest = captureBackgroundRequest()
            assertThat(bgRequest.command.workingDirectory).isEqualTo(Path("/my/project"))
        }

        @Test
        fun `includes all build commands in order`(): Unit = runBlocking {
            val request = buildRequest(commands = listOf("clean", "build", "bundle"))
            simulateSuccessfulBuild()

            builder.buildProject(request)

            val bgRequest = captureBackgroundRequest()
            val params = bgRequest.command.parametersList.parameters
            val cleanIdx = params.indexOf("clean")
            val buildIdx = params.indexOf("build")
            val bundleIdx = params.indexOf("bundle")

            assertThat(cleanIdx).isLessThan(buildIdx)
            assertThat(buildIdx).isLessThan(bundleIdx)
        }

        @Test
        fun `uses Java binary from config`(): Unit = runBlocking {
            every { config.javaBin } returns "/custom/java"
            val request = buildRequest()
            simulateSuccessfulBuild()

            builder.buildProject(request)

            val bgRequest = captureBackgroundRequest()
            assertThat(bgRequest.command.exePath).isEqualTo("/custom/java")
        }
    }

    @Nested
    inner class ErrorHandling {
        @Test
        fun `reports Bob exit code to console`(): Unit = runBlocking {
            val request = buildRequest()
            simulateFailedBuild(exitCode = 7)

            builder.buildProject(request)

            verify { console.print(match { it.contains("Bob build failed (exit code 7)") }, any()) }
        }

        @Test
        fun `handles missing Bob JAR gracefully`(): Unit = runBlocking {
            every { config.editorJar } returns ""
            val request = buildRequest()
            simulateSuccessfulBuild()

            val result = builder.buildProject(request)

            // Should still attempt to build, command construction doesn't validate paths
            assertThat(result.isSuccess).isTrue
        }

        @Test
        fun `propagates callback exceptions from onSuccess`(): Unit = runBlocking {
            val expectedException = RuntimeException("Callback error")
            val request = buildRequest(onSuccess = { throw expectedException })
            simulateSuccessfulBuild()

            val result = builder.buildProject(request)

            assertThat(result.isFailure).isTrue
            assertThat(result.exceptionOrNull()).isEqualTo(expectedException)
        }

        @Test
        fun `propagates callback exceptions from onFailure`(): Unit = runBlocking {
            val expectedException = RuntimeException("Failure callback error")
            val request = buildRequest(onFailure = { throw expectedException })
            simulateFailedBuild(exitCode = 1)

            val result = builder.buildProject(request)

            assertThat(result.isFailure).isTrue
            assertThat(result.exceptionOrNull()).isEqualTo(expectedException)
        }
    }
}
