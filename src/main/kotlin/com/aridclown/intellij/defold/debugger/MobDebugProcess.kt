package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.Event.*
import com.aridclown.intellij.defold.debugger.MobDebugProtocol.CommandType
import com.aridclown.intellij.defold.debugger.MobDebugProtocol.CommandType.*
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.util.trySilently
import com.intellij.execution.process.OSProcessHandler
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
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
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
    private val console: ConsoleView,
    private val gameProcess: OSProcessHandler?
) : XDebugProcess(session) {

    private val logger = Logger.getInstance(MobDebugProcess::class.java)
    private val server = MobDebugServer(host, port, logger)
    private val protocol = MobDebugProtocol(server, logger)
    private val evaluator = MobDebugEvaluator(protocol)
    private val pathResolver = MobDebugPathResolver(project, pathMapper)

    // Track active remote breakpoint locations (path + line) for precise pause filtering.
    private val breakpointLocations = ConcurrentHashMap.newKeySet<BreakpointLocation>()

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
            .any { breakpointLocations.contains(it, line) }
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
        console.print("Listening for MobDebug server at $host:$port...\n", NORMAL_OUTPUT)
    }

    override fun getEditorsProvider() = MobDebugEditorsProvider

    override fun stop() {
        protocol.exit()
        trySilently { gameProcess?.destroyProcess() }
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

        // Mirror Lua stdout/stderr into the IDE console
        protocol.outputStdout('r')
        protocol.outputStderr('r')
        resendAllBreakpoints()

        // MobDebug attaches in a suspended state
        // RUN on init allows the game to continue until a breakpoint or explicit pause; otherwise, it freezes
        protocol.run()
        session.consoleView.print("Connected to MobDebug server at $host:$port\n", NORMAL_OUTPUT)
    }

    private fun onServerDisconnected() {
        // Drop the client, keep listening
        server.restart()
        session.consoleView.print("Disconnected from MobDebug server at $host:$port\n", NORMAL_OUTPUT)
    }

    private fun resendAllBreakpoints() = getDefoldBreakpoints()
        .forEach { bp ->
            val pos = bp.sourcePosition ?: return@forEach
            val localPath = pos.file.path
            val remoteLine = pos.line + 1 // MobDebug line numbers are 1-based
            pathResolver.computeRemoteCandidates(localPath).forEach { remote ->
                breakpointLocations.add(remote, remoteLine)
                protocol.setBreakpoint(remote, remoteLine)
            }
        }

    private fun onPaused(evt: Paused) {
        val lc = lastControlCommand
        val isUserBased = lc == STEP || lc == OVER || lc == OUT || lc == SUSPEND

        if (isUserBased) {
            lastControlCommand = null
            buildSuspendContext(evt)
            return
        }

        if (!hasActiveBreakpointFor(evt.file, evt.line)) {
            protocol.run()
            return
        }

        val localPath = pathResolver.resolveLocalPath(evt.file)
        val breakpoint = findMatchingBreakpoint(localPath, evt.line)
        val condition = breakpoint?.conditionExpression?.expression?.trim()

        if (!condition.isNullOrEmpty()) {
            evaluator.evaluateExpr(
                frameIndex = 3,
                expr = condition,
                onSuccess = onSuccess@{ value ->
                    if (lastControlCommand == RUN) {
                        protocol.run()
                        return@onSuccess
                    }

                    if (value.toboolean()) {
                        buildSuspendContext(evt)
                    } else {
                        protocol.run()
                    }
                },
                onError = onError@{ _ ->
                    if (lastControlCommand == RUN) {
                        protocol.run()
                        return@onError
                    }

                    protocol.run()
                }
            )
        } else {
            buildSuspendContext(evt)
        }
    }

    private fun buildSuspendContext(evt: Paused) {
        val file = pathResolver.resolveLocalPath(evt.file)
        protocol.stack(
            options = "{ maxlevel = 0 }",
            onResult = { dump ->
                val infos = MobDebugStackParser.parseStackDump(dump)
                val frames = infos.mapIndexed { idx, info ->
                    val localPath = pathResolver.resolveLocalPath(info.source ?: evt.file)
                    MobDebugStackFrame(project, localPath, info.line ?: evt.line, info.variables, evaluator, idx + 3)
                }.ifEmpty {
                    // If stack info is unavailable, default to top user frame level (3)
                    listOf(MobDebugStackFrame(project, file, evt.line, emptyList(), evaluator, 3))
                }

                val context = MobDebugSuspendContext(frames)
                getApplication().invokeLater {
                    println("Execution paused at ${evt.file}:${evt.line}")
                    session.positionReached(context)
                }
            }
        )
    }

    private fun findMatchingBreakpoint(localPath: String?, line: Int) = getDefoldBreakpoints()
        .firstOrNull { bp ->
            val pos = bp.sourcePosition
            pos != null && pos.file.path == localPath && pos.line == line - 1
        }

    private fun getDefoldBreakpoints(): Collection<XLineBreakpoint<XBreakpointProperties<*>>> =
        XDebuggerManager.getInstance(project)
            .breakpointManager
            .getBreakpoints(DefoldScriptBreakpointType::class.java)

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
        val type = if (evt.stream.equals("stderr", ignoreCase = true)) ERROR_OUTPUT else NORMAL_OUTPUT
        console.print(evt.text, type)
    }

    private fun onOk(@Suppress("UNUSED_PARAMETER") evt: Ok) {
        // No-op: useful for correlation callbacks when needed (e.g., STACK)
    }
}
