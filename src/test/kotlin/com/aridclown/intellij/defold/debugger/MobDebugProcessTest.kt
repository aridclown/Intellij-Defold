package com.aridclown.intellij.defold.debugger

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpointManager
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

class MobDebugProcessTest {

    private lateinit var breakpointManager: XBreakpointManager
    private lateinit var application: Application

    @BeforeEach
    fun setUp() {
        mockkStatic(XDebuggerManager::class)
        mockkStatic(ApplicationManager::class)
        application = mockk(relaxed = true)
        every { ApplicationManager.getApplication() } returns application
        every { application.invokeLater(any()) } answers {
            firstArg<Runnable>().run()
        }
        breakpointManager = mockk(relaxed = true)
        every {
            breakpointManager.getBreakpoints(DefoldScriptBreakpointType::class.java)
        } returns emptyList()
        every { breakpointManager.removeBreakpoint(any()) } just Runs

        val debuggerManager = mockk<XDebuggerManager>(relaxed = true)
        every { debuggerManager.breakpointManager } returns breakpointManager
        every { XDebuggerManager.getInstance(any()) } returns debuggerManager
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(XDebuggerManager::class)
        unmockkStatic(ApplicationManager::class)
    }

    @Test
    fun `sends BASEDIR handshake when remote root provided`() {
        val result = runHandshake(localBaseDir = "/local/game", remoteBaseDir = "/remote/game")

        assertThat(result.commands).isNotEmpty
        assertThat(result.commands.first()).isEqualTo("BASEDIR /remote/game/")
    }

    @Test
    fun `falls back to local base dir when remote root missing`() {
        val result = runHandshake(localBaseDir = "C:\\workspace\\game", remoteBaseDir = "")

        assertThat(result.commands).isNotEmpty
        assertThat(result.commands.first()).isEqualTo("BASEDIR C:/workspace/game/")
    }

    @Test
    fun `uses project base dir when no explicit roots are provided`() {
        val result = runHandshake(localBaseDir = null, remoteBaseDir = null, projectBase = "/project/base")

        assertThat(result.commands).isNotEmpty
        assertThat(result.commands.first()).isEqualTo("BASEDIR /project/base/")
    }

    @Test
    fun `does not send BASEDIR when no directories are available`() {
        val result = runHandshake(localBaseDir = null, remoteBaseDir = null, projectBase = null)

        assertThat(result.commands.none { it.startsWith("BASEDIR") }).isTrue()
    }

    @Test
    fun `does not send disabled breakpoints on attach`() {
        val disabled = mockBreakpoint(
            localPath = "/local/scripts/main.script",
            line = 3,
            enabled = false
        )

        val result = runHandshake(
            localBaseDir = "/local",
            remoteBaseDir = null,
            projectBase = "/local",
            breakpoints = listOf(disabled)
        )

        assertThat(result.commands.none { it.startsWith("SETB") }).isTrue()
    }

    @Test
    fun `sends only enabled breakpoints on attach`() {
        val disabled = mockBreakpoint(
            localPath = "/local/scripts/disabled.script",
            line = 7,
            enabled = false
        )
        val enabled = mockBreakpoint(
            localPath = "/local/scripts/enabled.script",
            line = 4,
            enabled = true
        )

        val result = runHandshake(
            localBaseDir = "/local",
            remoteBaseDir = null,
            projectBase = "/local",
            breakpoints = listOf(disabled, enabled)
        )

        val setCommands = result.commands.filter { it.startsWith("SETB") }
        assertThat(setCommands).containsExactly(
            "SETB scripts/enabled.script 5",
            "SETB @scripts/enabled.script 5"
        )
    }

    @Test
    fun `skips breakpoint synchronization when session starts muted`() {
        val enabled = mockBreakpoint(
            localPath = "/local/scripts/enabled.script",
            line = 9,
            enabled = true
        )

        val result = runHandshake(
            localBaseDir = "/local",
            remoteBaseDir = null,
            projectBase = "/local",
            breakpoints = listOf(enabled),
            mutedInitially = true
        )

        assertThat(result.commands.none { it.startsWith("SETB") }).isTrue()
    }

    @Test
    fun `restores breakpoints when session is unmuted`() {
        val enabled = mockBreakpoint(
            localPath = "/local/scripts/enabled.script",
            line = 4,
            enabled = true
        )

        val result = runHandshake(
            localBaseDir = "/local",
            remoteBaseDir = null,
            projectBase = "/local",
            breakpoints = listOf(enabled),
            mutedInitially = true
        )

        assertThat(result.commands.none { it.startsWith("SETB") }).isTrue()

        val handler = result.process.getBreakpointHandlers().single() as MobDebugBreakpointHandler
        handler.registerBreakpoint(enabled)

        val setCommands = result.commands.filter { it.startsWith("SETB") }
        assertThat(setCommands).containsExactly(
            "SETB scripts/enabled.script 5",
            "SETB @scripts/enabled.script 5"
        )
    }

    @Test
    fun `non suspending breakpoint resumes after logging`() {
        val breakpoint = mockBreakpoint(
            localPath = "/local/scripts/enabled.script",
            line = 4,
            enabled = true,
            suspendPolicy = SuspendPolicy.NONE,
            logMessage = true
        )

        val context = createPauseContext(breakpoint) { session ->
            every { session.breakpointReached(any(), any(), any()) } returns false
        }

        context.triggerPause("scripts/enabled.script", 5)

        verify { context.session.breakpointReached(breakpoint, null, any()) }
        verify(exactly = 2) { context.protocol.run() }
        verify(exactly = 0) { context.session.positionReached(any()) }
    }

    @Test
    fun `remove once hit clears breakpoint even without suspension`() {
        val breakpoint = mockBreakpoint(
            localPath = "/local/scripts/temp.script",
            line = 2,
            enabled = true,
            suspendPolicy = SuspendPolicy.NONE,
            removeOnceHit = true
        )

        val context = createPauseContext(breakpoint) { session ->
            every { session.breakpointReached(any(), any(), any()) } returns false
        }

        context.triggerPause("scripts/temp.script", 3)

        verify { breakpointManager.removeBreakpoint(breakpoint) }
    }

    @Test
    fun `log expression result forwarded to session`() {
        val breakpoint = mockBreakpoint(
            localPath = "/local/scripts/enabled.script",
            line = 6,
            enabled = true,
            logExpression = "\"hello\""
        )

        val context = createPauseContext(breakpoint) { session ->
            every { session.breakpointReached(breakpoint, any(), any()) } answers {
                val logValue = secondArg<String?>()
                assertThat(logValue).isEqualTo("hello")
                true
            }
        }

        every {
            context.protocol.exec(any(), any(), any(), any(), any())
        } answers {
            val onResult = arg<(String) -> Unit>(3)
            onResult.invoke("return { \"\\\"hello\\\"\" }")
        }

        context.triggerPause("scripts/enabled.script", 7)

        verify { context.session.breakpointReached(breakpoint, "hello", any()) }
        verify(exactly = 1) { context.protocol.run() }
    }

    private data class HandshakeResult(
        val commands: MutableList<String>,
        val process: MobDebugProcess
    )

    private fun runHandshake(
        localBaseDir: String?,
        remoteBaseDir: String?,
        projectBase: String? = "/local",
        breakpoints: Collection<XLineBreakpoint<XBreakpointProperties<*>>> = emptyList(),
        mutedInitially: Boolean = false
    ): HandshakeResult {
        val project = mockk<Project>(relaxed = true) {
            every { basePath } returns projectBase
        }

        val console = mockk<ConsoleView>(relaxed = true)

        val session = mockk<XDebugSession>(relaxed = true, moreInterfaces = arrayOf(Disposable::class)) {
            every { consoleView } returns console
            every { resume() } just Runs
            every { setPauseActionSupported(any()) } just Runs
            every { stop() } just Runs
            every { areBreakpointsMuted() } returns mutedInitially
        }

        val config = mockk<MobDebugRunConfiguration>(relaxed = true) {
            every { host } returns "localhost"
            every { port } returns 9000
            every { localRoot } returns (localBaseDir ?: "")
            every { remoteRoot } returns (remoteBaseDir ?: "")
        }

        val server = mockk<MobDebugServer>(relaxed = true)
        val connected = slot<() -> Unit>()
        val commands = mutableListOf<String>()

        every { server.addListener(any()) } just Runs
        every { server.addOnConnectedListener(capture(connected)) } just Runs
        every { server.addOnDisconnectedListener(any()) } just Runs
        every { server.startServer() } just Runs
        every { server.dispose() } just Runs
        every { server.requestBody(any(), any()) } just Runs
        every { server.send(any()) } answers {
            commands += firstArg<String>()
        }

        every {
            breakpointManager.getBreakpoints(DefoldScriptBreakpointType::class.java)
        } returns breakpoints

        val process = MobDebugProcess(
            session = session,
            pathMapper = MobDebugPathMapper(emptyMap()),
            configData = config,
            project = project,
            console = console,
            gameProcess = null,
            serverFactory = { _, _, _ -> server },
            protocolFactory = { srv, _ -> MobDebugProtocol(srv, mockk(relaxed = true)) }
        )

        check(connected.isCaptured) { "Expected connection listener" }
        connected.captured.invoke()

        return HandshakeResult(commands, process)
    }

    private data class PauseContext(
        val process: MobDebugProcess,
        val protocol: MobDebugProtocol,
        val session: XDebugSession,
        val triggerPause: (String, Int) -> Unit
    )

    private fun createPauseContext(
        breakpoint: XLineBreakpoint<XBreakpointProperties<*>>,
        configureSession: (XDebugSession) -> Unit = {}
    ): PauseContext {
        val project = mockk<Project>(relaxed = true) {
            every { basePath } returns "/local"
        }
        val console = mockk<ConsoleView>(relaxed = true)

        every {
            breakpointManager.getBreakpoints(DefoldScriptBreakpointType::class.java)
        } returns listOf(breakpoint)

        val session = mockk<XDebugSession>(relaxed = true, moreInterfaces = arrayOf(Disposable::class)) {
            every { consoleView } returns console
            every { areBreakpointsMuted() } returns false
            every { setPauseActionSupported(any()) } just Runs
            every { stop() } just Runs
        }

        val protocol = mockk<MobDebugProtocol>(relaxed = true)
        val server = mockk<MobDebugServer>(relaxed = true)

        val listener = slot<(Event) -> Unit>()
        every { protocol.addListener(capture(listener)) } just Runs

        val paused = AtomicReference<Event.Paused?>()
        every { protocol.stack(any(), any(), any()) } answers {
            val event = paused.get() ?: error("No paused event")
            val dump = stackDumpFor(event.file, event.line)
            arg<(String) -> Unit>(1).invoke(dump)
        }

        every { protocol.clearAllBreakpoints(any()) } just Runs
        every { protocol.setBreakpoint(any(), any(), any()) } just Runs
        every { protocol.deleteBreakpoint(any(), any(), any()) } just Runs
        every { protocol.outputStdout(any(), any()) } just Runs
        every { protocol.basedir(any(), any()) } just Runs
        every { protocol.run() } just Runs
        every { protocol.exit() } just Runs

        val connected = slot<() -> Unit>()
        every { server.addOnConnectedListener(capture(connected)) } just Runs
        every { server.addOnDisconnectedListener(any()) } just Runs
        every { server.addListener(any()) } just Runs
        every { server.startServer() } just Runs
        every { server.dispose() } just Runs
        every { server.requestBody(any(), any()) } just Runs
        every { server.send(any()) } just Runs

        val config = mockk<MobDebugRunConfiguration>(relaxed = true) {
            every { host } returns "localhost"
            every { port } returns 9000
            every { localRoot } returns ""
            every { remoteRoot } returns ""
        }

        configureSession(session)

        val process = MobDebugProcess(
            session = session,
            pathMapper = MobDebugPathMapper(emptyMap()),
            configData = config,
            project = project,
            console = console,
            gameProcess = null,
            serverFactory = { _, _, _ -> server },
            protocolFactory = { _, _ -> protocol }
        )

        connected.captured.invoke()

        val trigger: (String, Int) -> Unit = { file, line ->
            val event = Event.Paused(file, line)
            paused.set(event)
            listener.captured.invoke(event)
        }

        return PauseContext(process, protocol, session, trigger)
    }

    private fun stackDumpFor(remoteFile: String, line: Int): String = """
        return {
          current = {
            id = "main",
            status = "running",
            frameBase = 3,
            stack = {
              {
                { "main", "@${remoteFile}", ${line}, 0 },
                {},
                {},
              },
            },
          },
          coroutines = {},
        }
    """.trimIndent()

    private fun mockBreakpoint(
        localPath: String,
        line: Int,
        enabled: Boolean,
        suspendPolicy: SuspendPolicy = SuspendPolicy.ALL,
        logMessage: Boolean = false,
        logStack: Boolean = false,
        logExpression: String? = null,
        removeOnceHit: Boolean = false,
        condition: String? = null
    ): XLineBreakpoint<XBreakpointProperties<*>> {
        val file = mockk<VirtualFile> {
            every { path } returns localPath
        }
        val position = mockk<XSourcePosition> {
            every { this@mockk.file } returns file
            every { this@mockk.line } returns line
        }

        return mockk(relaxed = true) {
            every { isEnabled } returns enabled
            every { sourcePosition } returns position
            every { conditionExpression } returns condition?.let { expressionOf(it) }
            every { getSuspendPolicy() } returns suspendPolicy
            every { isLogMessage } returns logMessage
            every { isLogStack } returns logStack
            every { logExpressionObject } returns logExpression?.let { expressionOf(it) }
            every { isTemporary } returns removeOnceHit
        }
    }

    private fun expressionOf(text: String): XExpression = mockk {
        every { expression } returns text
        every { language } returns null
        every { customInfo } returns null
    }
}
