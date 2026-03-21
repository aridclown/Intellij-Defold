package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.util.printError
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class ProjectRunnerHttpTest {
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `defaults to build`() {
        val command = ProjectRunner.editorCommandFor(listOf("build"), enableDebugScript = false)

        assertThat(command).isEqualTo("build")
    }

    @Test
    fun `uses rebuild when distclean requested`() {
        val command = ProjectRunner.editorCommandFor(listOf("distclean", "build"), enableDebugScript = false)

        assertThat(command).isEqualTo("rebuild")
    }

    @Test
    fun `uses debugger start when debug script enabled`() {
        val command = ProjectRunner.editorCommandFor(listOf("build"), enableDebugScript = true)

        assertThat(command).isEqualTo("debugger-start")
    }

    @Test
    fun `delegation is disabled by default`() {
        val request = RunRequest(
            project = mockk(relaxed = true),
            config = mockk(relaxed = true),
            console = mockk(relaxed = true)
        )

        assertThat(request.delegateToEditor).isFalse()
    }

    @Test
    fun `delegates exclusively to editor for normal run when selected`() {
        val request = runRequest(delegateToEditor = true)

        assertThat(ProjectRunner.shouldDelegateRunToEditor(request)).isTrue()
    }

    @Test
    fun `keeps local run path for debug requests even when delegation is selected`() {
        val request = runRequest(
            delegateToEditor = true,
            debugPort = 8172
        )

        assertThat(ProjectRunner.shouldDelegateRunToEditor(request)).isFalse()
    }

    @Test
    fun `reports unsupported editor command as a failure instead of falling back to bob`(): Unit = runBlocking {
        mockkObject(EditorHttpClient.Companion)
        val client = mockk<EditorHttpClient>()
        every { EditorHttpClient.connect(any()) } returns client
        every { client.supports("build") } returns false

        val result = ProjectRunner.runViaEditor(
            runRequest(delegateToEditor = true)
        )

        assertThat(result)
            .isEqualTo(EditorRunDelegationResult.Failed("The Defold editor does not support 'build'"))
    }

    @Test
    fun `delegates accepted command to editor and reports success`(): Unit = runBlocking {
        mockkObject(EditorHttpClient.Companion)

        val client = mockk<EditorHttpClient>()
        every { EditorHttpClient.connect(any()) } returns client
        every { client.supports("build") } returns true
        every { client.sendCommand("build") } returns true

        var terminationCode: Int? = null
        val console = mockk<ConsoleView>(relaxed = true)
        val result = ProjectRunner.runViaEditor(
            runRequest(
                delegateToEditor = true,
                console = console,
                onTermination = { terminationCode = it }
            )
        )

        assertThat(result).isEqualTo(EditorRunDelegationResult.Succeeded)
        assertThat(terminationCode).isEqualTo(0)
        verify {
            console.print(match { it.contains("Delegated 'build'") }, NORMAL_OUTPUT)
        }
    }

    @Test
    fun `prints clear error when exclusive editor delegation fails`() {
        val console = mockk<ConsoleView>(relaxed = true)
        val failure = EditorRunDelegationResult.Failed("The Defold editor rejected 'build'")

        when (failure) {
            EditorRunDelegationResult.Succeeded -> Unit
            is EditorRunDelegationResult.Failed -> console.printError("Run in Defold editor failed: ${failure.reason}")
        }

        verify {
            console.print(
                "Run in Defold editor failed: The Defold editor rejected 'build'\n",
                ERROR_OUTPUT
            )
        }
    }

    private fun runRequest(
        delegateToEditor: Boolean = false,
        debugPort: Int? = null,
        console: ConsoleView = mockk(relaxed = true),
        onTermination: (Int) -> Unit = {}
    ): RunRequest {
        val project = mockk<Project>(relaxed = true)
        every { project.basePath } returns "/tmp/project"

        return RunRequest(
            project = project,
            config = mockk(relaxed = true),
            console = console,
            debugPort = debugPort,
            delegateToEditor = delegateToEditor,
            onTermination = onTermination
        )
    }
}
