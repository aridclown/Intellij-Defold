@file:OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)

package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.util.NotificationService
import com.aridclown.intellij.defold.util.NotificationService.notifyError
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class DefoldEditorLauncherTest {
    private val projectPath = "/workspace/myproject"

    private val project = mockk<Project>(relaxed = true)
    private val commandBuilder = mockk<DefoldCommandBuilder>(relaxed = true)
    private val command = mockk<GeneralCommandLine>(relaxed = true)
    private val process = mockk<Process>(relaxed = true)

    private lateinit var launcher: DefoldEditorLauncher

    @BeforeEach
    fun setUp() {
        mockkObject(NotificationService)
        justRun { project.notifyError(any(), any()) }
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(NotificationService)
        clearAllMocks()
    }

    @Test
    fun `executes command created by builder`() = runTest {
        val harness = prepareLauncher()
        mockProcessWithExitCode(exitCode = 0)

        val job = launcher.openDefoldEditor(projectPath)
        advanceUntilIdle()
        job.join()

        assertThat(job.isCompleted).isTrue
        assertThat(harness.exceptions).isEmpty()
        verify(exactly = 1) { commandBuilder.createLaunchCommand(projectPath) }
    }

    @Test
    fun `throws error when process exits with non-zero code`() = runTest {
        val harness = prepareLauncher()
        mockProcessWithExitCode(exitCode = 1)

        val job = launcher.openDefoldEditor(projectPath)
        advanceUntilIdle()

        val exception = harness.captureFailure(job)
        assertThat(exception)
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Command exited with code 1")
    }

    @Test
    fun `propagates IOException from process creation`() = runTest {
        val harness = prepareLauncher()
        mockProcessCreationFailure(IOException("Failed to start"))

        val job = launcher.openDefoldEditor(projectPath)
        advanceUntilIdle()

        val exception = harness.captureFailure(job)
        assertThat(exception)
            .isInstanceOf(IOException::class.java)
            .hasMessage("Failed to start")
        verify { project.notifyError("Defold", "Failed to open Defold editor: Failed to start") }
    }

    @Test
    fun `rethrows ProcessCanceledException without notification`() = runTest {
        val harness = prepareLauncher()
        mockProcessCreationFailure(ProcessCanceledException())

        val job = launcher.openDefoldEditor(projectPath)
        advanceUntilIdle()

        val exception = harness.captureFailure(job)
        assertThat(exception).isInstanceOf(ProcessCanceledException::class.java)
        verify(exactly = 0) { project.notifyError(any(), any()) }
    }

    @Test
    fun `propagates InterruptedException`() = runTest {
        val harness = prepareLauncher()
        mockProcessThrowingException(InterruptedException("Interrupted"))

        val job = launcher.openDefoldEditor(projectPath)
        advanceUntilIdle()

        val exception = harness.captureFailure(job)
        assertThat(exception).isInstanceOf(InterruptedException::class.java)
        verify(exactly = 1) { process.waitFor() }
        Thread.interrupted() // clear interrupt flag restored by executeAndWait
    }

    private fun TestScope.prepareLauncher(): ServiceHarness = createHarness().apply {
        every { project.service<DefoldCoroutineService>() } returns service
        launcher = DefoldEditorLauncher(project, commandBuilder)
    }

    private fun TestScope.createHarness(): ServiceHarness {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val exceptions = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, throwable -> exceptions += throwable }
        val scope = CoroutineScope(SupervisorJob() + dispatcher + handler)
        return ServiceHarness(DefoldCoroutineService(scope), exceptions)
    }

    // Mock helpers
    private fun mockProcessWithExitCode(exitCode: Int) {
        every { commandBuilder.createLaunchCommand(projectPath) } returns command
        every { command.createProcess() } returns process
        every { process.waitFor() } returns exitCode
    }

    private fun mockProcessCreationFailure(exception: Throwable) {
        every { commandBuilder.createLaunchCommand(projectPath) } returns command
        every { command.createProcess() } throws exception
    }

    private fun mockProcessThrowingException(exception: Throwable) {
        every { commandBuilder.createLaunchCommand(projectPath) } returns command
        every { command.createProcess() } returns process
        every { process.waitFor() } throws exception
    }

    private fun ServiceHarness.resolveException(
        thrown: Throwable?,
        job: Job
    ): Throwable {
        val recorded = exceptions.singleOrNull()
        val cancellation = (thrown as? CancellationException) ?: job.getCancellationException()
        return recorded ?: cancellation.cause ?: cancellation
    }

    private suspend fun ServiceHarness.captureFailure(job: Job): Throwable {
        val thrown = runCatching { job.join() }.exceptionOrNull()
        return resolveException(thrown, job).also {
            assertThat(job.isCancelled).isTrue
        }
    }
}

private data class ServiceHarness(
    val service: DefoldCoroutineService,
    val exceptions: MutableList<Throwable>
)
