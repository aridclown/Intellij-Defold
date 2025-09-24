package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldEditorConfig
import com.aridclown.intellij.defold.DefoldProjectRunner
import com.aridclown.intellij.defold.process.DeferredProcessHandler
import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DefoldProgramRunnersTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Nested
    inner class BaseRunner {

        private val project = mockk<Project>(relaxed = true)
        private val console = mockk<ConsoleView>(relaxed = true) {
            every { print(any(), any()) } just Runs
        }

        private val stubbedRunner = object : BaseDefoldProgramRunner() {
            override fun getRunnerId(): String = "test"
            override fun canRun(executorId: String, profile: RunProfile): Boolean = false

            fun launch(
                enableDebugScript: Boolean = false,
                debugPort: Int? = null,
                envData: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
                onStarted: (OSProcessHandler) -> Unit
            ): Boolean = launch(project, console, enableDebugScript, debugPort, envData, onStarted)
        }

        @Test
        fun `launchBuild reports missing config`() {
            mockkObject(DefoldEditorConfig.Companion)
            mockkObject(DefoldProjectRunner)

            stubMissingConfig()

            var callbackInvoked = false
            val result = stubbedRunner.launch { callbackInvoked = true }

            assertThat(result).isFalse()
            assertThat(callbackInvoked).isFalse()
            verify(exactly = 1) { console.print("Invalid Defold editor path.\n", ERROR_OUTPUT) }
            verify(exactly = 0) { DefoldProjectRunner.run(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `launchBuild delegates to project runner`() {
            mockkObject(DefoldEditorConfig.Companion)
            mockkObject(DefoldProjectRunner)

            val handler = mockk<OSProcessHandler>()
            val config = stubSuccessfulBuild(project, console, handler, expectedEnableDebugScript = false)

            var received: OSProcessHandler? = null
            val result = stubbedRunner.launch { received = it }

            assertThat(result).isTrue()
            assertThat(received).isEqualTo(handler)
            verify(exactly = 0) { console.print(any(), any()) }
            verify(exactly = 1) { DefoldProjectRunner.run(project, config, console, any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class ProjectRunRunner {

        private val project = mockk<Project>(relaxed = true)
        private val console = mockConsole()

        @BeforeEach
        fun setUp() {
            mockConsoleFactory(project, console)
            mockkStatic(ApplicationManager::class)
            val app = mockk<Application>(relaxed = true)
            every { ApplicationManager.getApplication() } returns app
            every { app.invokeLater(any<Runnable>()) } answers {
                firstArg<Runnable>().run()
            }

            val uiFactory = mockk<RunnerLayoutUi.Factory>(relaxed = true)
            every { app.getService(RunnerLayoutUi.Factory::class.java) } returns uiFactory
            every { project.getService(RunnerLayoutUi.Factory::class.java) } returns uiFactory

            mockkObject(DefoldEditorConfig.Companion)
            mockkObject(DefoldProjectRunner)
            mockkConstructor(RunContentBuilder::class)
            mockkConstructor(DeferredProcessHandler::class)
        }

        @Test
        fun `canRun permits run executor only`() {
            val runner = DefoldProjectRunProgramRunner()
            val profile = mockk<MobDebugRunConfiguration>()

            assertThat(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, profile)).isTrue()
            assertThat(runner.canRun(DefaultDebugExecutor.EXECUTOR_ID, profile)).isFalse()
            assertThat(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, mockk())).isFalse()
        }

        @Test
        fun `doExecute attaches engine handler when build succeeds`() {
            val descriptor = mockk<RunContentDescriptor>()
            every { anyConstructed<RunContentBuilder>().showRunContent(any()) } returns descriptor

            val handler = mockEngineHandler()
            stubSuccessfulBuild(
                project = project,
                console = console,
                handler = handler,
                expectedEnableDebugScript = false
            ) { _, port ->
                assertThat(port).isNull()
            }

            val runConfig = mockk<MobDebugRunConfiguration>(relaxed = true) {
                every { envData } returns EnvironmentVariablesData.DEFAULT
            }
            val environment = executionEnvironment(project, DefaultRunExecutor.EXECUTOR_ID, runConfig)

            val processHandlerSlot = slot<DeferredProcessHandler>()
            every { console.attachToProcess(capture(processHandlerSlot)) } just Runs

            val attachedHandler = slot<OSProcessHandler>()
            mockkConstructor(DeferredProcessHandler::class)
            every { anyConstructed<DeferredProcessHandler>().attach(capture(attachedHandler)) } just Runs

            val runner = TestDefoldProjectRunProgramRunner()
            val result = runner.execute(mockk(relaxed = true), environment)

            assertThat(result).isEqualTo(descriptor)
            assertThat(attachedHandler.captured).isEqualTo(handler)
            verify(exactly = 1) { console.attachToProcess(processHandlerSlot.captured) }
            verify(exactly = 1) { anyConstructed<RunContentBuilder>().showRunContent(any()) }
            verify(exactly = 0) { console.print("Invalid Defold editor path.\n", ERROR_OUTPUT) }
        }

        @Test
        fun `doExecute reports invalid config`() {
            val descriptor = mockk<RunContentDescriptor>()
            every { anyConstructed<RunContentBuilder>().showRunContent(any()) } returns descriptor

            stubMissingConfig()

            val runConfig = mockk<MobDebugRunConfiguration>(relaxed = true) {
                every { envData } returns EnvironmentVariablesData.DEFAULT
            }
            val environment = executionEnvironment(project, DefaultRunExecutor.EXECUTOR_ID, runConfig)

            val runner = TestDefoldProjectRunProgramRunner()
            val result = runner.execute(mockk(relaxed = true), environment)

            assertThat(result).isEqualTo(descriptor)
            verify(exactly = 0) { anyConstructed<DeferredProcessHandler>().attach(any()) }
            verify(exactly = 1) { console.attachToProcess(any()) }
            verify(exactly = 1) { anyConstructed<RunContentBuilder>().showRunContent(any()) }
            verify(exactly = 0) { DefoldProjectRunner.run(any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class MobDebugRunner {

        private val project = mockk<Project>(relaxed = true)
        private val console = mockConsole()

        @BeforeEach
        fun setUp() {
            mockConsoleFactory(project, console)
            mockkObject(DefoldEditorConfig.Companion)
            mockkObject(DefoldProjectRunner)
            mockkStatic(XDebuggerManager::class)
        }

        @Test
        fun `canRun permits debug executor only`() {
            val runner = DefoldProjectDebugProgramRunner()
            val profile = mockk<MobDebugRunConfiguration>()

            assertThat(runner.canRun(DefaultDebugExecutor.EXECUTOR_ID, profile)).isTrue()
            assertThat(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, profile)).isFalse()
            assertThat(runner.canRun(DefaultDebugExecutor.EXECUTOR_ID, mockk())).isFalse()
        }

        @Test
        fun `doExecute starts debug session and triggers build`() {
            val descriptor = mockk<RunContentDescriptor>()
            val session = mockk<XDebugSession>(relaxed = true) {
                every { runContentDescriptor } returns descriptor
                every { consoleView } returns console
            }

            val manager = mockk<XDebuggerManager>()
            var createdProcess: MobDebugProcess? = null
            every { manager.startSession(any(), any()) } answers {
                val starter = secondArg<XDebugProcessStarter>()
                createdProcess = starter.start(session) as MobDebugProcess
                session
            }
            every { XDebuggerManager.getInstance(project) } returns manager

            val handler = mockEngineHandler()
            val debugPort = 8123
            val config = stubSuccessfulBuild(
                project = project,
                console = console,
                handler = handler,
                expectedEnableDebugScript = true
            ) { _, port ->
                assertThat(port).isEqualTo(debugPort)
            }

            val runConfig = mockk<MobDebugRunConfiguration> {
                every { host } returns "localhost"
                every { port } returns debugPort
                every { localRoot } returns "/local"
                every { remoteRoot } returns "/remote"
                every { getMappingSettings() } returns mapOf("/local" to "/remote")
                every { envData } returns EnvironmentVariablesData.DEFAULT
            }

            val environment = executionEnvironment(project, DefaultDebugExecutor.EXECUTOR_ID, runConfig)

            val runner = TestDefoldProjectDebugProgramRunner()
            val result = runner.execute(mockk(relaxed = true), environment)

            assertThat(result).isEqualTo(descriptor)
            assertThat(createdProcess).isNotNull()
            verify(exactly = 1) { DefoldProjectRunner.run(project, config, console, any(), any(), any(), any()) }
            verify(exactly = 0) { console.print("Invalid Defold editor path.\n", ERROR_OUTPUT) }
        }

        @Test
        fun `doExecute reports invalid config`() {
            val descriptor = mockk<RunContentDescriptor>()
            val session = mockk<XDebugSession>(relaxed = true) {
                every { runContentDescriptor } returns descriptor
                every { consoleView } returns console
            }
            val manager = mockk<XDebuggerManager>()
            every { manager.startSession(any(), any()) } answers {
                val starter = secondArg<XDebugProcessStarter>()
                starter.start(session)
                session
            }
            every { XDebuggerManager.getInstance(project) } returns manager

            stubMissingConfig()

            val runConfig = mockk<MobDebugRunConfiguration>(relaxed = true) {
                every { envData } returns EnvironmentVariablesData.DEFAULT
            }
            every { runConfig.localRoot } returns ""
            every { runConfig.remoteRoot } returns ""

            val environment = executionEnvironment(project, DefaultDebugExecutor.EXECUTOR_ID, runConfig)

            val runner = TestDefoldProjectDebugProgramRunner()
            val result = runner.execute(mockk(relaxed = true), environment)

            assertThat(result).isEqualTo(descriptor)
            verify(exactly = 1) { manager.startSession(environment, any()) }
            verify(exactly = 0) { DefoldProjectRunner.run(any(), any(), any(), any(), any(), any(), any()) }
        }
    }
}

private fun mockConsole(): ConsoleView = mockk(relaxed = true) {
    every { component } returns mockk(relaxed = true)
    every { print(any(), any()) } just Runs
    every { attachToProcess(any()) } just Runs
}

private fun mockConsoleFactory(project: Project, console: ConsoleView) {
    mockkStatic(TextConsoleBuilderFactory::class)
    val factory = mockk<TextConsoleBuilderFactory>()
    val builder = mockk<TextConsoleBuilder>()

    every { TextConsoleBuilderFactory.getInstance() } returns factory
    every { factory.createBuilder(project) } returns builder
    every { builder.console } returns console
}

private fun mockEngineHandler(): OSProcessHandler = mockk(relaxed = true) {
    every { isProcessTerminated } returns false
    every { process } returns mockk(relaxed = true) {
        every { pid() } returns 123L
    }
    every { addProcessListener(any()) } just Runs
}

private fun stubMissingConfig() {
    every { DefoldEditorConfig.loadEditorConfig() } returns null
    every { DefoldProjectRunner.run(any(), any(), any(), any(), any(), any(), any()) } just Runs
}

private fun stubSuccessfulBuild(
    project: Project,
    console: ConsoleView,
    handler: OSProcessHandler,
    expectedEnableDebugScript: Boolean? = null,
    onRunInvoked: ((Boolean, Int?) -> Unit)? = null
): DefoldEditorConfig {
    val config = mockk<DefoldEditorConfig>()
    every { DefoldEditorConfig.loadEditorConfig() } returns config

    every {
        DefoldProjectRunner.run(
            project = project,
            config = config,
            console = console,
            enableDebugScript = any(),
            debugPort = any(),
            envData = any(),
            onEngineStarted = any()
        )
    } answers {
        val enable = invocation.args[3] as Boolean
        val port = invocation.args[4] as Int?
        @Suppress("UNCHECKED_CAST")
        val callback = invocation.args[6] as (OSProcessHandler) -> Unit

        expectedEnableDebugScript?.let { assertThat(enable).isEqualTo(it) }
        onRunInvoked?.invoke(enable, port)
        callback(handler)
    }

    return config
}

private fun executionEnvironment(
    project: Project,
    executorId: String,
    runProfile: RunProfile
): ExecutionEnvironment {
    val executor = mockk<Executor>(relaxed = true)
    every { executor.id } returns executorId

    return mockk(relaxed = true) {
        every { this@mockk.project } returns project
        every { this@mockk.executor } returns executor
        every { this@mockk.runProfile } returns runProfile
        every { contentToReuse } returns null
        every { executionId } returns 99L
    }
}

private class TestDefoldProjectRunProgramRunner : DefoldProjectRunProgramRunner() {
    fun execute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? =
        super.doExecute(state, environment)
}

private class TestDefoldProjectDebugProgramRunner : DefoldProjectDebugProgramRunner() {
    fun execute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor =
        super.doExecute(state, environment)
}
