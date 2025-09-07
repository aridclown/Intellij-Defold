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
    private val breakpointFiles: ConcurrentHashMap.KeySetView<String, Boolean>
) : XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<*>>>(
    DefoldScriptBreakpointType::class.java
) {

    override fun registerBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>) {
        val pos = breakpoint.sourcePosition ?: return
        val localPath = pos.file.path
        for (remote in pathResolver.computeRemoteCandidates(localPath)) {
            // Track remote file forms for filtering pause events
            breakpointFiles.add(remote)
            protocol.setBreakpoint(remote, pos.line + 1)
        }
    }

    override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>, temporary: Boolean) {
        val pos = breakpoint.sourcePosition ?: return
        val localPath = pos.file.path
        for (remote in pathResolver.computeRemoteCandidates(localPath)) {
            protocol.deleteBreakpoint(remote, pos.line + 1)
            breakpointFiles.remove(remote)
        }
    }
}
