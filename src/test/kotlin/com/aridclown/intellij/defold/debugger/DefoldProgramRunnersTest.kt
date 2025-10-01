package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldEditorConfig
import com.aridclown.intellij.defold.DefoldProjectRunner
import com.aridclown.intellij.defold.DefoldRunRequest
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
        clearAllMocks()
        unmockkAll()
    }

    @Nested
    inner class BaseRunner {

        private val project = mockk<Project>(relaxed = true)
        private val console = mockConsole()

        private val stubbedRunner = object : BaseDefoldProgramRunner() {
            override fun getRunnerId(): String = "test"
            override fun canRun(executorId: String, profile: RunProfile): Boolean = false

            fun launchRequest(request: DefoldRunRequest?): Boolean = super.launch(request)
        }

        @Test
        fun `launch returns false when request is missing`() {
            mockkObject(DefoldProjectRunner)
            every { DefoldProjectRunner.run(any()) } just Runs

            val result = stubbedRunner.launchRequest(null)

            assertThat(result).isFalse()
            verify(exactly = 0) { DefoldProjectRunner.run(any()) }
        }

        @Test
        fun `launch delegates to project runner`() {
            mockkObject(DefoldProjectRunner)
            val config = mockk<DefoldEditorConfig>()
            val request = DefoldRunRequest(
                project = project,
                config = config,
                console = console,
                enableDebugScript = false,
                onEngineStarted = { it.addProcessListener(mockk(relaxed = true)) }
            )

            every { DefoldProjectRunner.run(any()) } answers {
                val captured = firstArg<DefoldRunRequest>()
                assertThat(captured).isEqualTo(request)
            }

            val result = stubbedRunner.launchRequest(request)

            assertThat(result).isTrue()
            verify(exactly = 1) { DefoldProjectRunner.run(request) }
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
            val editorConfig = mockk<DefoldEditorConfig>()
            every { DefoldEditorConfig.loadEditorConfig() } returns editorConfig

            val envData = EnvironmentVariablesData.create(mapOf("FOO" to "BAR"), true)
            val runtimeCommands = listOf("bundle")
            val runtimeDebugFlag = true
            val runConfig = mockk<MobDebugRunConfiguration>(relaxed = true) {
                every { this@mockk.envData } returns envData
                every { this@mockk.runtimeBuildCommands } returns runtimeCommands
                every { this@mockk.runtimeBuildCommands = any() } just Runs
                every { this@mockk.runtimeEnableDebugScript } returns runtimeDebugFlag
                every { this@mockk.runtimeEnableDebugScript = any() } just Runs
            }
            val environment = executionEnvironment(project, DefaultRunExecutor.EXECUTOR_ID, runConfig)

            val processHandlerSlot = slot<DeferredProcessHandler>()
            every { console.attachToProcess(capture(processHandlerSlot)) } just Runs

            val attachedHandler = slot<OSProcessHandler>()
            every { anyConstructed<DeferredProcessHandler>().attach(capture(attachedHandler)) } just Runs

            val requestSlot = slot<DefoldRunRequest>()
            every { DefoldProjectRunner.run(capture(requestSlot)) } answers {
                requestSlot.captured.onEngineStarted(handler)
            }

            val runner = TestDefoldProjectRunProgramRunner()
            val result = runner.execute(mockk(relaxed = true), environment)

            assertThat(result).isEqualTo(descriptor)
            assertThat(attachedHandler.captured).isEqualTo(handler)
            verify(exactly = 1) { console.attachToProcess(processHandlerSlot.captured) }
            verify(exactly = 1) { anyConstructed<RunContentBuilder>().showRunContent(any()) }
            verify(exactly = 0) { console.print("Invalid Defold editor path.\n", ERROR_OUTPUT) }
            verify(exactly = 1) { DefoldProjectRunner.run(any()) }

            val request = requestSlot.captured
            assertThat(request.project).isEqualTo(project)
            assertThat(request.console).isEqualTo(console)
            assertThat(request.config).isEqualTo(editorConfig)
            assertThat(request.enableDebugScript).isTrue()
            assertThat(request.debugPort).isNull()
            assertThat(request.envData).isEqualTo(envData)
            assertThat(request.buildCommands).containsExactly("bundle")
        }

        @Test
        fun `doExecute reports invalid config`() {
            val descriptor = mockk<RunContentDescriptor>()
            every { anyConstructed<RunContentBuilder>().showRunContent(any()) } returns descriptor

            every { DefoldEditorConfig.loadEditorConfig() } returns null
            every { DefoldProjectRunner.run(any()) } just Runs

            val runtimeCommands = listOf("bundle")
            val runtimeDebugFlag = false
            val runConfig = mockk<MobDebugRunConfiguration>(relaxed = true) {
                every { this@mockk.envData } returns EnvironmentVariablesData.DEFAULT
                every { this@mockk.runtimeBuildCommands } returns runtimeCommands
                every { this@mockk.runtimeBuildCommands = any() } just Runs
                every { this@mockk.runtimeEnableDebugScript } returns runtimeDebugFlag
                every { this@mockk.runtimeEnableDebugScript = any() } just Runs
            }
            val environment = executionEnvironment(project, DefaultRunExecutor.EXECUTOR_ID, runConfig)

            val runner = TestDefoldProjectRunProgramRunner()
            val result = runner.execute(mockk(relaxed = true), environment)

            assertThat(result).isEqualTo(descriptor)
            verify(exactly = 0) { anyConstructed<DeferredProcessHandler>().attach(any()) }
            verify(exactly = 1) { console.attachToProcess(any()) }
            verify(exactly = 1) { anyConstructed<RunContentBuilder>().showRunContent(any()) }
            verify(exactly = 0) { DefoldProjectRunner.run(any()) }
            verify(exactly = 1) { console.print("Invalid Defold editor path.\n", ERROR_OUTPUT) }
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
            val editorConfig = mockk<DefoldEditorConfig>()
            every { DefoldEditorConfig.loadEditorConfig() } returns editorConfig

            val envData = EnvironmentVariablesData.create(mapOf("FOO" to "BAR"), true)
            val runtimeCommands = listOf("bundle", "hotreload")
            val runtimeDebugFlag = false
            val runConfig = mockk<MobDebugRunConfiguration> {
                every { host } returns "localhost"
                every { port } returns debugPort
                every { localRoot } returns "/local"
                every { remoteRoot } returns "/remote"
                every { getMappingSettings() } returns mapOf("/local" to "/remote")
                every { this@mockk.envData } returns envData
                every { this@mockk.runtimeBuildCommands } returns runtimeCommands
                every { this@mockk.runtimeBuildCommands = any() } just Runs
                every { this@mockk.runtimeEnableDebugScript } returns runtimeDebugFlag
                every { this@mockk.runtimeEnableDebugScript = any() } just Runs
            }

            val requestSlot = slot<DefoldRunRequest>()
            every { DefoldProjectRunner.run(capture(requestSlot)) } answers {
                requestSlot.captured.onEngineStarted(handler)
            }

            val environment = executionEnvironment(project, DefaultDebugExecutor.EXECUTOR_ID, runConfig)

            val runner = TestDefoldProjectDebugProgramRunner()
            val result = runner.execute(mockk(relaxed = true), environment)

            assertThat(result).isEqualTo(descriptor)
            assertThat(createdProcess).isNotNull()
            verify(exactly = 1) { DefoldProjectRunner.run(any()) }
            verify(exactly = 0) { console.print("Invalid Defold editor path.\n", ERROR_OUTPUT) }

            val request = requestSlot.captured
            assertThat(request.project).isEqualTo(project)
            assertThat(request.console).isEqualTo(console)
            assertThat(request.config).isEqualTo(editorConfig)
            assertThat(request.enableDebugScript).isFalse()
            assertThat(request.debugPort).isEqualTo(debugPort)
            assertThat(request.envData).isEqualTo(envData)
            assertThat(request.buildCommands).containsExactly("bundle", "hotreload")
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

            every { DefoldEditorConfig.loadEditorConfig() } returns null
            every { DefoldProjectRunner.run(any()) } just Runs

            val runtimeCommands = listOf("build")
            val runtimeDebugFlag: Boolean? = null
            val runConfig = mockk<MobDebugRunConfiguration>(relaxed = true) {
                every { this@mockk.envData } returns EnvironmentVariablesData.DEFAULT
                every { this@mockk.runtimeBuildCommands } returns runtimeCommands
                every { this@mockk.runtimeBuildCommands = any() } just Runs
                every { this@mockk.runtimeEnableDebugScript } returns runtimeDebugFlag
                every { this@mockk.runtimeEnableDebugScript = any() } just Runs
                every { host } returns "localhost"
                every { port } returns 8123
                every { localRoot } returns ""
                every { remoteRoot } returns ""
                every { getMappingSettings() } returns emptyMap()
            }

            val environment = executionEnvironment(project, DefaultDebugExecutor.EXECUTOR_ID, runConfig)

            val runner = TestDefoldProjectDebugProgramRunner()
            val result = runner.execute(mockk(relaxed = true), environment)

            assertThat(result).isEqualTo(descriptor)
            verify(exactly = 1) { manager.startSession(environment, any()) }
            verify(exactly = 0) { DefoldProjectRunner.run(any()) }
            verify(exactly = 1) { console.print("Invalid Defold editor path.\n", ERROR_OUTPUT) }
        }
    }
}

private fun mockConsole(): ConsoleView = mockk(relaxed = true) {
    every { component } returns mockk(relaxed = true)
    every { print(any(), any()) } just Runs
    every { attachToProcess(any()) } just Runs
    every { addMessageFilter(any()) } just Runs
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
