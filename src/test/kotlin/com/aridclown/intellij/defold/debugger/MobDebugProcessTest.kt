package com.aridclown.intellij.defold.debugger

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpointManager
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MobDebugProcessTest {

    private lateinit var breakpointManager: XBreakpointManager

    @BeforeEach
    fun setUp() {
        mockkStatic(XDebuggerManager::class)
        breakpointManager = mockk(relaxed = true)
        every {
            breakpointManager.getBreakpoints(DefoldScriptBreakpointType::class.java)
        } returns emptyList()

        val debuggerManager = mockk<XDebuggerManager>(relaxed = true)
        every { debuggerManager.breakpointManager } returns breakpointManager
        every { XDebuggerManager.getInstance(any()) } returns debuggerManager
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(XDebuggerManager::class)
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

    private fun mockBreakpoint(
        localPath: String,
        line: Int,
        enabled: Boolean
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
            every { conditionExpression } returns null
        }
    }
}
