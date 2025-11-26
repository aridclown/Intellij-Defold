package com.aridclown.intellij.defold.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.fixture.projectFixture
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

@TestApplication
@TestFixtures
class ProcessExecutorIntegrationTest {
    private val projectFixture = projectFixture()

    private lateinit var project: Project
    private val printed = mutableListOf<Pair<String, ConsoleViewContentType>>()
    private val attachedHandlers = mutableListOf<ProcessHandler>()
    private lateinit var executor: ProcessExecutor

    @BeforeEach
    fun setUp() {
        project = projectFixture.get()
        executor = ProcessExecutor(mockkConsole())
    }

    @Test
    fun `execute attaches console and runs process`(): Unit = timeoutRunBlocking {
        val handler = executor.execute(successCommand())
        handler.waitFor()

        assertThat(handler.isProcessTerminated).isTrue
        assertThat(attachedHandlers).contains(handler)
    }

    @Test
    fun `executeAndWait returns exit code`(): Unit = timeoutRunBlocking {
        val exit = executor.executeAndWait(successCommand())

        assertThat(exit).isZero()
        assertThat(attachedHandlers).isNotEmpty
    }

    @Test
    fun `executeInBackground triggers success callback`(): Unit = timeoutRunBlocking {
        var successCalled = false
        val job =
            executor.executeInBackground(
                BackgroundProcessRequest(
                    project = project,
                    title = "Run",
                    command = successCommand(),
                    onSuccess = { successCalled = true }
                )
            )

        job.join()

        assertThat(successCalled).isTrue
    }

    @Test
    fun `executeInBackground triggers failure callback`(): Unit = timeoutRunBlocking {
        var failureCode: Int? = null
        val job =
            executor.executeInBackground(
                BackgroundProcessRequest(
                    project = project,
                    title = "Run",
                    command = failureCommand(),
                    onFailure = { failureCode = it }
                )
            )

        job.join()

        assertThat(failureCode)
            .isNotNull
            .isGreaterThan(0)
    }

    @Test
    fun `executeInBackground logs errors when process fails to start`(): Unit = timeoutRunBlocking {
        val missingExecutable = GeneralCommandLine(createTempDirectory().resolve("missing_executable").toString())
        val job =
            executor.executeInBackground(
                BackgroundProcessRequest(project, "Run", missingExecutable)
            )

        job.join()

        assertThat(printedText()).startsWith("Process execution failed")
    }

    private fun successCommand(): GeneralCommandLine = when {
        SystemInfo.isWindows -> GeneralCommandLine("cmd.exe", "/c", "echo $SUCCESS_MARKER")
        else -> GeneralCommandLine("/bin/sh", "-c", "echo $SUCCESS_MARKER")
    }

    private fun failureCommand(): GeneralCommandLine = when {
        SystemInfo.isWindows -> GeneralCommandLine("cmd.exe", "/c", "exit 5")
        else -> GeneralCommandLine("/bin/sh", "-c", "exit 5")
    }

    private fun mockkConsole(): ConsoleView = mockk<ConsoleView>(relaxed = true).apply {
        every { attachToProcess(any()) } answers {
            attachedHandlers += firstArg<ProcessHandler>()
        }
        every { print(any(), any()) } answers {
            val text = firstArg<String>()
            val type = secondArg<ConsoleViewContentType>()
            printed += text to type
        }
        every { performWhenNoDeferredOutput(any()) } answers {
            firstArg<Runnable>().run()
        }
    }

    private fun printedText(): String = printed.joinToString(separator = "") { it.first }

    companion object {
        private const val SUCCESS_MARKER = "ProcessExecutorSuccess"
    }
}
