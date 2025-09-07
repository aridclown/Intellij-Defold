package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.Event.*
import com.aridclown.intellij.defold.debugger.MobDebugProtocol.CommandType
import com.aridclown.intellij.defold.debugger.MobDebugProtocol.CommandType.*
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.frame.XSuspendContext
import java.util.concurrent.ConcurrentHashMap

/**
 * XDebugProcess that talks to a running MobDebug server.
 * Only a minimal subset of the protocol is implemented.
 */
class MobDebugProcess(
    session: XDebugSession,
    pathMapper: MobDebugPathMapper,
    private val project: Project,
    private val host: String,
    private val port: Int,
    private val console: ConsoleView
) : XDebugProcess(session) {

    private val logger = Logger.getInstance(MobDebugProcess::class.java)
    private val server = MobDebugServer(host, port, logger)
    private val protocol = MobDebugProtocol(server, logger)
    private val pathResolver = MobDebugPathResolver(project, pathMapper)

    // Track active remote breakpoint locations (remotePath:line) for precise pause filtering.
    private val breakpointLocations = ConcurrentHashMap.newKeySet<String>()

    private fun locationKey(remotePath: String, line: Int): String = "$remotePath:$line"

    private fun hasActiveBreakpointFor(remoteFile: String, line: Int): Boolean {
        val plain = when {
            remoteFile.startsWith("@") -> remoteFile.substring(1)
            else -> remoteFile
        }
        val withAt = when {
            plain.startsWith("@") -> plain
            else -> "@$plain"
        }

        return listOf(remoteFile, plain, withAt)
            .any { breakpointLocations.contains(locationKey(it, line)) }
    }

    @Volatile
    private var lastControlCommand: CommandType? = null

    init {
        // Mirror raw traffic in the console for troubleshooting
        server.addListener { line -> console.print(line + "\n", NORMAL_OUTPUT) }

        // React to parsed protocol events
        protocol.addListener { event ->
            when (event) {
                is Paused -> onPaused(event)
                is Error -> onError(event)
                is Output -> onOutput(event)
                is Ok -> onOk(event)
                is Unknown -> logger.warn("Unhandled MobDebug response: ${event.line}")
            }
        }

        // Reconnect lifecycle similar to EmmyLua
        server.addOnConnectedListener { onServerConnected() }
        server.addOnDisconnectedListener { onServerDisconnected() }
    }

    override fun createConsole(): ConsoleView = console

    override fun sessionInitialized() {
        server.startServer()
        session.setPauseActionSupported(true)

        // MobDebug attaches in a suspended state
        // RUN on init allows the game to continue until a breakpoint or explicit pause; otherwise, it freezes
        protocol.run()
        logServerConnected()
    }

    override fun getEditorsProvider() = MobDebugEditorsProvider

    override fun stop() {
        protocol.exit()
        server.dispose()
    }

    override fun resume(context: XSuspendContext?) {
        lastControlCommand = RUN; protocol.run()
    }

    override fun startPausing() {
        lastControlCommand = SUSPEND; protocol.suspend()
    }

    override fun startStepOver(context: XSuspendContext?) {
        lastControlCommand = OVER; protocol.over()
    }

    override fun startStepInto(context: XSuspendContext?) {
        lastControlCommand = STEP; protocol.step()
    }

    override fun startStepOut(context: XSuspendContext?) {
        lastControlCommand = OUT; protocol.out()
    }

    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> =
        arrayOf(MobDebugBreakpointHandler(protocol, pathResolver, breakpointLocations))

    private fun onServerConnected() {
        // After reconnect: clear remote breakpoints and re-send current ones
        breakpointLocations.clear()
        protocol.clearAllBreakpoints()
        resendAllBreakpoints()
        logServerConnected()
    }

    private fun onServerDisconnected() {
        // Drop client, keep listening (EmmyLua MobServer.restart())
        server.restart()
    }

    private fun resendAllBreakpoints() {
        XDebuggerManager.getInstance(project)
            .breakpointManager
            .getBreakpoints(DefoldScriptBreakpointType::class.java)
            .forEach { bp ->
                val pos = bp.sourcePosition ?: return@forEach
                val localPath = pos.file.path
                val remoteLine = pos.line + 1
                pathResolver.computeRemoteCandidates(localPath).forEach { remote ->
                    breakpointLocations.add(locationKey(remote, remoteLine))
                    protocol.setBreakpoint(remote, remoteLine) // MobDebug line numbers are 1-based
                }
            }
    }

    private fun onPaused(evt: Paused) {
        val lc = lastControlCommand
        val isUserBased = lc == STEP || lc == OVER || lc == OUT || lc == SUSPEND

        if (isUserBased) {
            // Consume the control command; allow this pause
            lastControlCommand = null
        } else {
            // Filter free-running pauses: allow only if paused file:line matches an active breakpoint
            val allowed = hasActiveBreakpointFor(evt.file, evt.line)
            if (!allowed) {
                protocol.run()
                return
            }
        }

        val file = pathResolver.resolveLocalPath(evt.file)
        val frame = MobDebugStackFrame(project, file, evt.line)
        val context = MobDebugSuspendContext(listOf(frame))
        getApplication().invokeLater {
            println("Execution paused at ${evt.file}:${evt.line}")
            session.positionReached(context)
        }
    }

    private fun onError(evt: Error) {
        getApplication().invokeLater {
            val msg = buildString {
                append("MobDebug error: ")
                append(evt.message)
                if (!evt.details.isNullOrBlank()) append("\n").append(evt.details)
            }
            console.print(msg + "\n", ERROR_OUTPUT)
        }
    }

    private fun onOutput(evt: Output) {
        // For now, just mirror to console. In a later slice we may add toggles.
        console.print(evt.text, NORMAL_OUTPUT)
    }

    private fun onOk(@Suppress("UNUSED_PARAMETER") evt: Ok) {
        // No-op: useful for correlation callbacks when needed (e.g., STACK)
    }

    private fun logServerConnected() {
        session.consoleView.print("Connected to MobDebug server at $host:$port", NORMAL_OUTPUT)
    }
}
