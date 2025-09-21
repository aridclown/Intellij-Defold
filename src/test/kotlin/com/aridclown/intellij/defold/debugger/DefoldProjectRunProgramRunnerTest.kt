package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldEditorConfig
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefoldProjectRunProgramRunnerTest {

    private val project = mockk<Project>(relaxed = true)
    private val console = mockk<ConsoleView>(relaxed = true) {
        every { component } returns mockk(relaxed = true)
    }

    @Test
    fun `canRun returns true only for run executor`() {
        val runner = TestableDefoldProjectRunProgramRunner(consoleProvider = { console })
        val runProfile = mockk<DefoldMobDebugRunConfiguration>()

        assertTrue(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, runProfile))
        assertFalse(runner.canRun(DefaultDebugExecutor.EXECUTOR_ID, runProfile))
        assertFalse(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, mockk()))
    }

    @Test
    fun `doExecute launches build when editor config is available`() {
        val handler = mockk<OSProcessHandler>()
        var capturedProject: Project? = null
        var capturedConfig: DefoldEditorConfig? = null
        var capturedConsole: ConsoleView? = null

        val config = mockk<DefoldEditorConfig>()
        val runner = TestableDefoldProjectRunProgramRunner(
            consoleProvider = { console },
            configLoader = { config },
            buildLauncher = { project, cfg, view, onEngineStarted ->
                capturedProject = project
                capturedConfig = cfg
                capturedConsole = view
                onEngineStarted(handler)
            },
            edtInvoker = { task -> task() }
        )

        val environment = mockk<ExecutionEnvironment> {
            every { project } returns this@DefoldProjectRunProgramRunnerTest.project
            every { runProfile } returns mockk {
                every { name } returns "Defold MobDebug"
            }
            every { executionId } returns 42L
        }

        val descriptor = runner.execute(mockk(), environment)

        assertEquals(project, capturedProject)
        assertEquals(config, capturedConfig)
        assertEquals(console, capturedConsole)
        assertEquals(handler, descriptor.processHandler)
        Disposer.dispose(descriptor)
    }

    @Test
    fun `doExecute reports configuration error when editor config is missing`() {
        var buildInvoked = false
        val runner = TestableDefoldProjectRunProgramRunner(
            consoleProvider = { console },
            configLoader = { null },
            buildLauncher = { _, _, _, _ -> buildInvoked = true },
            edtInvoker = { task -> task() }
        )

        val environment = mockk<ExecutionEnvironment> {
            every { project } returns this@DefoldProjectRunProgramRunnerTest.project
            every { runProfile } returns mockk {
                every { name } returns "Defold MobDebug"
            }
            every { executionId } returns 7L
        }

        val descriptor = runner.execute(mockk(), environment)

        assertFalse(buildInvoked)
        assertNull(descriptor.processHandler)
        verify { console.print("Invalid Defold editor path.\n", ERROR_OUTPUT) }
        Disposer.dispose(descriptor)
    }
}

private class TestableDefoldProjectRunProgramRunner(
    consoleProvider: (Project) -> ConsoleView,
    configLoader: () -> DefoldEditorConfig? = { null },
    buildLauncher: (
        Project,
        DefoldEditorConfig,
        ConsoleView,
        (OSProcessHandler) -> Unit
    ) -> Unit = { _, _, _, _ -> },
    edtInvoker: (() -> Unit) -> Unit = { }
) : DefoldProjectRunProgramRunner(consoleProvider, configLoader, buildLauncher, edtInvoker) {

    fun execute(state: RunProfileState, environment: ExecutionEnvironment) =
        doExecute(state, environment)
}
