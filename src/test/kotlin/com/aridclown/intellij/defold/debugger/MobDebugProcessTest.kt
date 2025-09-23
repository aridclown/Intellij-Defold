package com.aridclown.intellij.defold.debugger

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointManager
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MobDebugProcessTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(XDebuggerManager::class)
        @Suppress("UNCHECKED_CAST")
        val breakpointManager = mockk<XBreakpointManager>(relaxed = true) {
            every { getBreakpoints(DefoldScriptBreakpointType::class.java) } returns
                emptyList<XLineBreakpoint<*>>() as Collection<XLineBreakpoint<XBreakpointProperties<*>>>
        }
        val debuggerManager = mockk<XDebuggerManager>(relaxed = true) {
            every { this@mockk.breakpointManager } returns breakpointManager
        }
        every { XDebuggerManager.getInstance(any()) } returns debuggerManager
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(XDebuggerManager::class)
    }

    @Test
    fun `sends BASEDIR handshake when remote root provided`() {
        val commands = runHandshake(localBaseDir = "/local/game", remoteBaseDir = "/remote/game")

        assertThat(commands).isNotEmpty
        assertThat(commands.first()).isEqualTo("BASEDIR /remote/game/")
    }

    @Test
    fun `falls back to local base dir when remote root missing`() {
        val commands = runHandshake(localBaseDir = "C:\\workspace\\game", remoteBaseDir = "")

        assertThat(commands).isNotEmpty
        assertThat(commands.first()).isEqualTo("BASEDIR C:/workspace/game/")
    }

    @Test
    fun `uses project base dir when no explicit roots are provided`() {
        val commands = runHandshake(localBaseDir = null, remoteBaseDir = null, projectBase = "/project/base")

        assertThat(commands).isNotEmpty
        assertThat(commands.first()).isEqualTo("BASEDIR /project/base/")
    }

    @Test
    fun `does not send BASEDIR when no directories are available`() {
        val commands = runHandshake(localBaseDir = null, remoteBaseDir = null, projectBase = null)

        assertThat(commands.none { it.startsWith("BASEDIR") }).isTrue()
    }

    private fun runHandshake(
        localBaseDir: String?,
        remoteBaseDir: String?,
        projectBase: String? = "/local"
    ): List<String> {
        val project = mockk<Project>(relaxed = true) {
            every { basePath } returns projectBase
        }

        val console = mockk<ConsoleView>(relaxed = true)

        val session = mockk<XDebugSession>(relaxed = true) {
            every { consoleView } returns console
            every { setPauseActionSupported(any()) } just Runs
            every { stop() } just Runs
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

        val protocolFactory: (MobDebugServer, Logger) -> MobDebugProtocol = { srv, _ ->
            MobDebugProtocol(srv, mockk(relaxed = true))
        }

        MobDebugProcess(
            session = session,
            pathMapper = MobDebugPathMapper(emptyMap()),
            project = project,
            host = "localhost",
            port = 9000,
            console = console,
            gameProcess = null,
            localBaseDir = localBaseDir,
            remoteBaseDir = remoteBaseDir,
            serverFactory = { _, _, _ -> server },
            protocolFactory = protocolFactory
        )

        check(connected.isCaptured) { "Expected connection listener" }
        connected.captured.invoke()

        return commands
    }
}
