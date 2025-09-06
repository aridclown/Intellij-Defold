package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.MobDebugProtocol.CommandType
import com.aridclown.intellij.defold.debugger.MobDebugProtocol.CommandType.*
import com.aridclown.intellij.defold.debugger.MobDebugProtocol.Event.*
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XSuspendContext
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * XDebugProcess that talks to a running MobDebug server.
 * Only a minimal subset of the protocol is implemented.
 */
class MobDebugProcess(
    session: XDebugSession,
    private val project: Project,
    private val host: String,
    private val port: Int,
    private val pathMapper: MobDebugPathMapper,
    private val console: ConsoleView
) : XDebugProcess(session) {

    private val logger = Logger.getInstance(MobDebugProcess::class.java)
    private val server = MobDebugServer(host, port, logger)
    private val protocol = MobDebugProtocol(server, logger)

    // Track files with registered breakpoints (remote paths as sent to server).
    private val breakpointFiles = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var lastControlCommand: CommandType? = null

    init {
        // Mirror raw traffic in console for troubleshooting
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
    }

    override fun createConsole(): ConsoleView = console

    override fun sessionInitialized() {
        server.startServer()
        session.setPauseActionSupported(true)
        session.consoleView.print("Connected to MobDebug server at $host:$port", NORMAL_OUTPUT)
        // Start the program (RUN is queued and sent on connect)
        protocol.run()
    }

    override fun getEditorsProvider(): XDebuggerEditorsProvider = MobDebugEditorsProvider

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

    private val breakpointHandler = MobDebugBreakpointHandler()

    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> = arrayOf(breakpointHandler)

    private inner class MobDebugBreakpointHandler : XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<*>>>(
        DefoldScriptBreakpointType::class.java
    ) {
        override fun registerBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>) {
            val pos = breakpoint.sourcePosition ?: return
            val localPath = pos.file.path
            for (remote in computeRemoteCandidates(localPath)) {
                // Track remote file forms for filtering pause events
                breakpointFiles.add(remote)
                protocol.setBreakpoint(remote, pos.line + 1)
            }
        }

        override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>, temporary: Boolean) {
            val pos = breakpoint.sourcePosition ?: return
            val localPath = pos.file.path
            for (remote in computeRemoteCandidates(localPath)) {
                protocol.deleteBreakpoint(remote, pos.line + 1)
                breakpointFiles.remove(remote)
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
            // Filter free-running pauses: allow only if the paused file matches registered breakpoints
            val fileRaw = evt.file
            val filePlain = if (fileRaw.startsWith("@")) fileRaw.substring(1) else fileRaw
            val allowed = breakpointFiles.contains(fileRaw)
                    || breakpointFiles.contains(filePlain)
                    || breakpointFiles.contains("@$filePlain")
            if (!allowed) {
                protocol.run()
                return
            }
        }

        val file = resolveLocalPath(evt.file)
        val frame = MobDebugStackFrame(project, file, evt.line)
        val context = MobDebugSuspendContext(listOf(frame))
        ApplicationManager.getApplication().invokeLater {
            println("Execution paused at ${evt.file}:${evt.line}")
            session.positionReached(context)
        }
    }

    private fun onError(evt: Error) {
        ApplicationManager.getApplication().invokeLater {
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

    private fun computeRelativeToProject(absoluteLocalPath: String): String? {
        val base = project.basePath ?: return null
        val basePath = Path.of(base).normalize()
        val absPath = Path.of(absoluteLocalPath).normalize()

        return when {
            absPath.startsWith(basePath) -> {
                val rel = basePath.relativize(absPath)
                FileUtil.toSystemIndependentName(rel.toString()).trimStart('/')
            }

            else -> null
        }
    }

    private fun computeRemoteCandidates(absoluteLocalPath: String): List<String> {
        val candidates = mutableSetOf<String>()
        val mapped = pathMapper.toRemote(absoluteLocalPath)
            ?.let { FileUtil.toSystemIndependentName(it) }
        val rel = computeRelativeToProject(absoluteLocalPath)
            ?.let { FileUtil.toSystemIndependentName(it) }

        val primary = mapped ?: rel
        if (primary != null) {
            candidates.add(primary)
            candidates.add("@$primary")
        }

        return candidates.toList()
    }

    private fun resolveLocalPath(remotePath: String): String? {
        // Try explicit mapping first
        val deChunked = if (remotePath.startsWith("@")) remotePath.substring(1) else remotePath
        val mapped = pathMapper.toLocal(deChunked)
        if (mapped != null) return mapped

        // If the remote path looks relative, try relative to the project base dir
        val base = project.basePath
        val si = FileUtil.toSystemIndependentName(deChunked)
        if (!si.startsWith("/") && base != null) {
            val local = Path.of(base).normalize().resolve(si.replace('/', File.separatorChar)).normalize()
            return FileUtil.toSystemIndependentName(local.toString())
        }

        return null
    }
}
