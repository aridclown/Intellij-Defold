package com.aridclown.intellij.defold.debugger

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XSuspendContext

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

    init {
        server.addListener { line ->
            console.print(line + "\n", NORMAL_OUTPUT)
            handleLine(line)
        }
    }

    override fun createConsole(): ConsoleView = console

    override fun sessionInitialized() {
        server.startServer()
        session.setPauseActionSupported(true)
        session.consoleView.print("Connected to MobDebug server at $host:$port", NORMAL_OUTPUT)
    }

    override fun getEditorsProvider(): XDebuggerEditorsProvider = MobDebugEditorsProvider

    override fun stop() {
        server.send("EXIT")
        server.dispose()
    }

    override fun resume(context: XSuspendContext?) = server.send("RUN")
    override fun startPausing() = server.send("SUSPEND")
    override fun startStepOver(context: XSuspendContext?) = server.send("OVER")
    override fun startStepInto(context: XSuspendContext?) = server.send("STEP")
    override fun startStepOut(context: XSuspendContext?) = server.send("OUT")

    private val breakpointHandler = MobDebugBreakpointHandler()

    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> = arrayOf(breakpointHandler)

    private inner class MobDebugBreakpointHandler : XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<*>>>(
        DefoldScriptBreakpointType::class.java
    ) {
        override fun registerBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>) {
            val pos = breakpoint.sourcePosition ?: return
            val localPath = pos.file.path
            for (remote in computeRemoteCandidates(localPath)) {
                server.send("SETB $remote ${pos.line + 1}")
            }
        }

        override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>, temporary: Boolean) {
            val pos = breakpoint.sourcePosition ?: return
            val localPath = pos.file.path
            for (remote in computeRemoteCandidates(localPath)) {
                server.send("DELB $remote ${pos.line + 1}")
            }
        }
    }

    private fun handleLine(line: String) {
        logger.info("MobDebug response: $line")

        when {
            // Execution suspended/paused (breakpoint hit, step, or manual pause)
            line.startsWith("202") -> {
                val parts = line.substringAfter("202 Paused").trim().split(" ")
                if (parts.size >= 2) {
                    val filePath = parts[0]
                    val lineNum = parts[1].toIntOrNull() ?: 0
                    val file = resolveLocalPath(filePath)
                    val frame = MobDebugStackFrame(project, file, lineNum, emptyList())
                    val context: XSuspendContext = MobDebugSuspendContext(listOf(frame))
                    ApplicationManager.getApplication().invokeLater {
                        println("Execution paused at $filePath:$lineNum")
                        session.positionReached(context)
                    }
                }
            }

            // Command accepted/execution resumed
            line.startsWith("200") -> {
                // For RUN commands, this means execution resumed
                // For other commands like SETB/DELB/STACK, just acknowledge
                println("Command acknowledged: $line")
            }

            // Bad request/invalid command
            line.startsWith("400") -> {
                ApplicationManager.getApplication().invokeLater {
                    println("MobDebug error: Invalid command\n$ERROR_OUTPUT")
                }
            }

            // Stack trace response (complex serialized data)
            line.contains("#lua/structure") -> {
                // TODO: Parse the complex Lua stack structure
                // For now, just log it
                logger.info("Stack trace received: ${line.take(100)}...")
                ApplicationManager.getApplication().invokeLater {
                    println("Stack trace available\n$NORMAL_OUTPUT")
                }
            }

            // Legacy SUSPEND format (your original handling)
            line.startsWith("SUSPEND") -> {
                val parts = line.split(" ")
                if (parts.size >= 2) {
                    val loc = parts[1].split(":")
                    if (loc.size == 2) {
                        val file = resolveLocalPath(loc[0])
                        val l = loc[1].toIntOrNull() ?: 0
                        val frame = MobDebugStackFrame(project, file, l, emptyList())
                        val context: XSuspendContext = MobDebugSuspendContext(listOf(frame))
                        ApplicationManager.getApplication().invokeLater {
                            println("Execution suspended at $file:$l")
                            session.positionReached(context)
                        }
                    }
                }
            }

            // Other error codes
            line.startsWith("401") || line.startsWith("404") || line.startsWith("500") -> {
                ApplicationManager.getApplication().invokeLater {
                    println("MobDebug error: $line\n$ERROR_OUTPUT")
                }
            }

            // Unknown response
            else -> {
                logger.warn("Unhandled MobDebug response: $line")
            }
        }
    }

    private fun computeRelativeToProject(absoluteLocalPath: String): String? {
        val base = project.basePath ?: return null
        val normBase = base.replace('\\', '/')
        val normAbs = absoluteLocalPath.replace('\\', '/')
        return if (normAbs.startsWith(normBase)) {
            normAbs.removePrefix(normBase).trimStart('/')
        } else null
    }

    private fun lastTwoSegments(path: String): String? {
        val parts = path.split('/')
        return when {
            parts.isEmpty() -> null
            parts.size == 1 -> parts.last()
            else -> parts.takeLast(2).joinToString("/")
        }
    }

    private fun computeRemoteCandidates(absoluteLocalPath: String): List<String> {
        val candidates = LinkedHashSet<String>()
        val mapped = pathMapper.toRemote(absoluteLocalPath)?.replace('\\', '/')
        val rel = computeRelativeToProject(absoluteLocalPath)?.replace('\\', '/')

        val primary = mapped ?: rel
        if (primary != null) {
            candidates.add(primary)
            candidates.add("@" + primary)
        }

        return candidates.toList()
    }

    private fun resolveLocalPath(remotePath: String): String? {
        // Try explicit mapping first
        val mapped = pathMapper.toLocal(remotePath)
        if (mapped != null) return mapped

        // If the remote path looks relative, try relative to project base dir
        val base = project.basePath
        if (!remotePath.startsWith("/") && base != null) {
            val local = (base.trimEnd('/') + "/" + remotePath.trimStart('/')).replace('\\', '/')
            return local
        }
        return null
    }
}
