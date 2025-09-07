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
    private val breakpointLocations: ConcurrentHashMap.KeySetView<String, Boolean>
) : XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<*>>>(
    DefoldScriptBreakpointType::class.java
) {

    private fun key(remotePath: String, line: Int): String = "$remotePath:$line"

    override fun registerBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>) {
        val pos = breakpoint.sourcePosition ?: return
        val localPath = pos.file.path
        val remoteLine = pos.line + 1
        for (remote in pathResolver.computeRemoteCandidates(localPath)) {
            protocol.setBreakpoint(remote, remoteLine)
            breakpointLocations.add(key(remote, remoteLine))
        }
    }

    override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>, temporary: Boolean) {
        val pos = breakpoint.sourcePosition ?: return
        val localPath = pos.file.path
        val remoteLine = pos.line + 1
        for (remote in pathResolver.computeRemoteCandidates(localPath)) {
            protocol.deleteBreakpoint(remote, remoteLine)
            breakpointLocations.remove(key(remote, remoteLine))
        }
    }
}
