package com.aridclown.intellij.defold.debugger

import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles breakpoint registration and unregistration for MobDebug protocol.
 * Uses PathResolver strategy for converting between local and remote paths.
 */
class MobDebugBreakpointHandler(
    private val protocol: MobDebugProtocol,
    private val pathResolver: PathResolver,
    private val breakpointLocations: ConcurrentHashMap.KeySetView<BreakpointLocation, Boolean>
) : XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<*>>>(
    DefoldScriptBreakpointType::class.java
) {

    override fun registerBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>) =
        processBreakpoint(breakpoint) { remote, remoteLine ->
            protocol.setBreakpoint(remote, remoteLine)
            breakpointLocations.add(remote, remoteLine)
        }

    override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>, temporary: Boolean) =
        processBreakpoint(breakpoint) { remote, remoteLine ->
            protocol.deleteBreakpoint(remote, remoteLine)
            breakpointLocations.remove(remote, remoteLine)
        }

    private fun processBreakpoint(
        breakpoint: XLineBreakpoint<XBreakpointProperties<*>>,
        action: (remote: String, remoteLine: Int) -> Unit
    ) {
        val pos = breakpoint.sourcePosition ?: return
        val localPath = pos.file.path
        val remoteLine = pos.line + 1
        pathResolver.computeRemoteCandidates(localPath).forEach { remote ->
            action(remote, remoteLine)
        }
    }
}
