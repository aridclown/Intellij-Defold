package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldConstants.STACK_MAXLEVEL
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
import com.intellij.openapi.util.io.FileUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.frame.XSuspendContext
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.util.concurrent.ConcurrentHashMap

typealias ServerFactory = (String, Int, Logger) -> MobDebugServer
typealias DebugProtocolFactory = (MobDebugServer, Logger) -> MobDebugProtocol

/**
 * XDebugProcess that talks to a running MobDebug server.
 * Only a minimal subset of the protocol is implemented.
 */
class MobDebugProcess(
    session: XDebugSession,
    pathMapper: MobDebugPathMapper,
    configData: MobDebugRunConfiguration,
    private val project: Project,
    private val console: ConsoleView,
    private val gameProcess: OSProcessHandler?,
    serverFactory: ServerFactory = { h, p, logger -> MobDebugServer(h, p, logger) },
    protocolFactory: DebugProtocolFactory = { server, logger -> MobDebugProtocol(server, logger) },
) : XDebugProcess(session) {

    private val logger = Logger.getInstance(MobDebugProcess::class.java)
    private val host: String = configData.host.ifBlank { "localhost" }
    private val port: Int = configData.port
    private val localBaseDir: String? = configData.localRoot.ifBlank { project.basePath }
    private val remoteBaseDir: String? = configData.remoteRoot.ifBlank { null }
    private val server = serverFactory(host, port, logger)
    private val protocol = protocolFactory(server, logger)
    private val evaluator = MobDebugEvaluator(protocol)
    private val pathResolver = MobDebugPathResolver(project, pathMapper)

    // Track active remote breakpoint locations (path + line) for precise pause filtering.
    private val breakpointLocations = ConcurrentHashMap.newKeySet<BreakpointLocation>()
    private val runToCursorBreakpoints = ConcurrentHashMap.newKeySet<BreakpointLocation>()

    companion object {
        private val MULTIPLE_SLASHES = Regex("/{2,}")
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

    override fun stopAsync(): Promise<in Any> {
        protocol.exit()
        trySilently { gameProcess?.destroyProcess() }
        session.stop()
        server.dispose()

        return resolvedPromise<Any>()
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

    override fun runToPosition(position: XSourcePosition, context: XSuspendContext?) {
        val localPath = position.file.path
        val remoteLine = position.line + 1
        val candidates = pathResolver.computeRemoteCandidates(localPath)

        clearRunToCursorBreakpoints()

        if (candidates.isEmpty()) {
            logger.warn("Run to cursor requested for $localPath, but no remote mapping was found")
            session.resume()
            return
        }

        candidates.forEach { remote ->
            protocol.setBreakpoint(remote, remoteLine)
            runToCursorBreakpoints.add(remote, remoteLine)
        }

        lastControlCommand = RUN
        protocol.run()
    }

    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> = arrayOf(
        MobDebugBreakpointHandler(protocol, pathResolver, breakpointLocations)
    )

    private fun onServerConnected() {
        negotiatedBaseDir()?.let(protocol::basedir)

        // After reconnect: clear remote breakpoints and re-send current ones
        resetBreakpoints()

        // Mirror Lua stdout/stderr into the IDE console
        protocol.outputStdout('r')
        protocol.outputStderr('r')

        // MobDebug attaches in a suspended state\
        // RUN on init allows the game to continue until a breakpoint or explicit pause; otherwise, it freezes
        protocol.run()
        session.consoleView.print("Connected to MobDebug server at $host:$port\n", NORMAL_OUTPUT)
    }

    private fun negotiatedBaseDir(): String? {
        val candidate = when {
            !remoteBaseDir.isNullOrBlank() -> remoteBaseDir
            !localBaseDir.isNullOrBlank() -> localBaseDir
            else -> project.basePath
        }?.trim()

        if (candidate.isNullOrBlank()) return null

        val normalized = FileUtil.toSystemIndependentName(candidate).trim()
        if (normalized.isBlank()) return null

        val collapsed = if (normalized.startsWith("//")) {
            "//" + normalized.removePrefix("//").replace(MULTIPLE_SLASHES, "/")
        } else {
            normalized.replace(MULTIPLE_SLASHES, "/")
        }

        return collapsed.trimEnd('/') + "/"
    }

    private fun onServerDisconnected() {
        // On disconnect, stop the debug session
        session.stop()
        session.consoleView.print("Disconnected from MobDebug server at $host:$port\n", NORMAL_OUTPUT)
    }

    private fun resetBreakpoints() {
        breakpointLocations.clear()
        runToCursorBreakpoints.clear()
        protocol.clearAllBreakpoints()

        // resend all breakpoints if not muted
        if (session.areBreakpointsMuted()) return

        getDefoldBreakpoints().forEach { bp ->
            if (!bp.isEnabled) return@forEach
            val pos = bp.sourcePosition ?: return@forEach
            val localPath = pos.file.path
            val remoteLine = pos.line + 1 // MobDebug line numbers are 1-based
            pathResolver.computeRemoteCandidates(localPath).forEach { remote ->
                breakpointLocations.add(remote, remoteLine)
                protocol.setBreakpoint(remote, remoteLine)
            }
        }
    }

    private fun onPaused(evt: Paused) {
        if (handleRunToCursorPause(evt)) {
            return
        }

        val lc = lastControlCommand
        when {
            lc.isUserBased() -> {
                lastControlCommand = null
                requestSuspendContext(evt, session::positionReached)
            }

            !hasActiveBreakpointFor(evt.file, evt.line) -> {
                protocol.run()
            }

            else -> {
                lastControlCommand = null

                val localPath = pathResolver.resolveLocalPath(evt.file)
                val breakpoint = findMatchingBreakpoint(localPath, evt.line)
                handleBreakpointHit(evt, breakpoint)
            }
        }
    }

    private fun CommandType?.isUserBased() = this in setOf(STEP, OVER, OUT, SUSPEND)

    private fun hasActiveBreakpointFor(remoteFile: String, line: Int): Boolean {
        val plain = when {
            remoteFile.startsWith("@") -> remoteFile.substring(1)
            else -> remoteFile
        }
        val withAt = when {
            plain.startsWith("@") -> plain
            else -> "@$plain"
        }

        return listOf(remoteFile, plain, withAt).any {
            breakpointLocations.contains(it, line) || runToCursorBreakpoints.contains(it, line)
        }
    }

    private fun handleRunToCursorPause(evt: Paused): Boolean {
        if (runToCursorBreakpoints.isEmpty()) {
            return false
        }

        val matched = isRunToCursorLocation(evt.file, evt.line)
        clearRunToCursorBreakpoints()

        if (!matched) {
            return false
        }

        lastControlCommand = null
        requestSuspendContext(evt, session::positionReached)
        return true
    }

    private fun handleBreakpointHit(
        evt: Paused,
        breakpoint: XLineBreakpoint<XBreakpointProperties<*>>?
    ) {
        if (breakpoint == null) {
            requestSuspendContext(evt, session::positionReached)
            return
        }

        val condition = breakpoint.conditionExpression?.expression?.trim()
        if (!condition.isNullOrEmpty()) {
            evaluator.evaluateExpr(
                frameIndex = 3,
                expr = condition,
                onSuccess = { value ->
                    if (value.toboolean()) {
                        processBreakpoint(evt, breakpoint)
                    } else {
                        protocol.run()
                    }
                },
                onError = {
                    console.print("Failed to evaluate breakpoint condition: ${it}\n", ERROR_OUTPUT)
                    protocol.run()
                }
            )
        } else {
            processBreakpoint(evt, breakpoint)
        }
    }

    private fun processBreakpoint(
        evt: Paused,
        breakpoint: XLineBreakpoint<XBreakpointProperties<*>>
    ) {
        evaluateLogExpression(breakpoint) { evaluated ->
            requestSuspendContext(evt) { context ->
                val shouldSuspend = session.breakpointReached(breakpoint, evaluated, context)
                if (!shouldSuspend) {
                    protocol.run()
                }
            }
        }
    }

    private fun evaluateLogExpression(
        breakpoint: XLineBreakpoint<XBreakpointProperties<*>>,
        onEvaluated: (String?) -> Unit
    ) {
        val expression = breakpoint.logExpressionObject?.expression?.takeIf { it.isNotBlank() }
        if (expression.isNullOrEmpty()) {
            onEvaluated(null)
            return
        }

        evaluator.evaluateExpr(
            frameIndex = 3,
            expr = expression,
            onSuccess = { value -> onEvaluated(value.tojstring()) },
            onError = {
                console.print("Failed to evaluate log expression: ${it}\n", ERROR_OUTPUT)
                onEvaluated(null)
            }
        )
    }

    private fun requestSuspendContext(evt: Paused, onReady: (MobDebugSuspendContext) -> Unit) {
        protocol.stack(
            options = "{ maxlevel = $STACK_MAXLEVEL }",
            onResult = { dump ->
                val stackDump = MobDebugStackParser.parseStackDump(dump)
                val executionStacks = MobDebugStackBuilder.buildExecutionStacks(
                    project = project,
                    evaluator = evaluator,
                    stackDump = stackDump,
                    pathResolver = pathResolver,
                    fallbackFile = evt.file,
                    fallbackLine = evt.line,
                    pausedFile = pathResolver.resolveLocalPath(evt.file)
                )

                val context = MobDebugSuspendContext(executionStacks)
                getApplication().invokeLater {
                    onReady(context)
                }
            }
        )
    }

    private fun findMatchingBreakpoint(localPath: String?, line: Int) = getDefoldBreakpoints().firstOrNull { bp ->
        val pos = bp.sourcePosition
        pos != null && pos.file.path == localPath && pos.line == line - 1
    }

    private fun getDefoldBreakpoints(): Collection<XLineBreakpoint<XBreakpointProperties<*>>> =
        XDebuggerManager.getInstance(project)
            .breakpointManager
            .getBreakpoints(DefoldScriptBreakpointType::class.java)

    private fun clearRunToCursorBreakpoints() {
        if (runToCursorBreakpoints.isEmpty()) return

        val locationsToRestore = runToCursorBreakpoints.filter { location ->
            breakpointLocations.contains(location.path, location.line)
        }

        runToCursorBreakpoints.forEach { location ->
            protocol.deleteBreakpoint(location.path, location.line)
        }
        runToCursorBreakpoints.clear()

        locationsToRestore.forEach { location ->
            protocol.setBreakpoint(location.path, location.line)
        }
    }

    private fun isRunToCursorLocation(remoteFile: String, line: Int): Boolean {
        val plain = when {
            remoteFile.startsWith("@") -> remoteFile.substring(1)
            else -> remoteFile
        }
        val withAt = when {
            plain.startsWith("@") -> plain
            else -> "@$plain"
        }

        return runToCursorBreakpoints.contains(remoteFile, line) ||
                runToCursorBreakpoints.contains(plain, line) ||
                runToCursorBreakpoints.contains(withAt, line)
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
        val type = if (evt.stream.equals("stderr", ignoreCase = true)) ERROR_OUTPUT else NORMAL_OUTPUT
        console.print(evt.text, type)
    }

    private fun onOk(@Suppress("UNUSED_PARAMETER") evt: Ok) {
        // No-op: useful for correlation callbacks when needed (e.g., STACK)
    }
}
