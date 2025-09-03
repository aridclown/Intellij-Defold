package com.aridclown.intellij.defold.debugger

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.*
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XSuspendContext

/**
 * XDebugProcess that talks to a running MobDebug server.
 * Only a minimal subset of the protocol is implemented.
 */
class MobDebugProcess(
    session: XDebugSession,
    private val project: Project,
    host: String,
    port: Int,
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

    override fun resume(context: XSuspendContext?) = client.send("RUN")
    override fun startPausing() = client.send("PAUSE")
    override fun startStepOver(context: XSuspendContext?) = client.send("OVER")
    override fun startStepInto(context: XSuspendContext?) = client.send("STEP")
    override fun startStepOut(context: XSuspendContext?) = client.send("OUT")

    private val breakpointHandler = MobDebugBreakpointHandler()

    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> = arrayOf(breakpointHandler)

    private inner class MobDebugBreakpointHandler : XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<*>>>(
        @Suppress("UNCHECKED_CAST")
        XLineBreakpointType::class.java as Class<out XBreakpointType<XLineBreakpoint<XBreakpointProperties<*>>, *>>
    ) {
        override fun registerBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>) {
            val pos = breakpoint.sourcePosition ?: return
            val remote = pathMapper.toRemote(pos.file.path) ?: return
            client.send("SETB $remote ${pos.line + 1}")
        }

        override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>, temporary: Boolean) {
            val pos = breakpoint.sourcePosition ?: return
            val remote = pathMapper.toRemote(pos.file.path) ?: return
            client.send("DELB $remote ${pos.line + 1}")
        }
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
