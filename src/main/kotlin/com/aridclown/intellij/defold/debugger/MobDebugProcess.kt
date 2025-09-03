package com.aridclown.intellij.defold.debugger

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.*
import com.intellij.xdebugger.breakpoints.XBreakpoint
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
    private val client = MobDebugClient(host, port, logger)

    init {
        client.addListener { line ->
            console.print(line + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
            handleLine(line)
        }
    }

    override fun sessionInitialized() {
        client.connect()
        client.send("RUN")
    }

    override fun getEditorsProvider(): XDebuggerEditorsProvider = MobDebugEditorsProvider

    override fun stop() {
        client.send("EXIT")
        client.dispose()
    }

    override fun resume() = client.send("RUN")
    override fun startPausing() = client.send("PAUSE")
    override fun startStepOver() = client.send("OVER")
    override fun startStepInto() = client.send("STEP")
    override fun startStepOut() = client.send("OUT")

    override fun registerBreakpoint(breakpoint: XBreakpoint<*>, temporary: Boolean) {
        val pos = breakpoint.sourcePosition ?: return
        val remote = pathMapper.toRemote(pos.file.path) ?: return
        client.send("SETB $remote ${pos.line + 1}")
    }

    override fun unregisterBreakpoint(breakpoint: XBreakpoint<*>, temporary: Boolean) {
        val pos = breakpoint.sourcePosition ?: return
        val remote = pathMapper.toRemote(pos.file.path) ?: return
        client.send("DELB $remote ${pos.line + 1}")
    }

    private fun handleLine(line: String) {
        if (line.startsWith("SUSPEND")) {
            val parts = line.split(" ")
            if (parts.size >= 2) {
                val loc = parts[1].split(":")
                if (loc.size == 2) {
                    val file = pathMapper.toLocal(loc[0])
                    val l = loc[1].toIntOrNull() ?: 0
                    val frame = MobDebugStackFrame(project, file, l, emptyList())
                    val context: XSuspendContext = MobDebugSuspendContext(listOf(frame))
                    ApplicationManager.getApplication().invokeLater {
                        session.positionReached(context)
                    }
                }
            }
        }
    }
}
