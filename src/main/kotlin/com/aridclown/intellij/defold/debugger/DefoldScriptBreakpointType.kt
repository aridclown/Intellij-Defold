package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldProjectService.Companion.getService
import com.aridclown.intellij.defold.DefoldScriptType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpointType

class DefoldScriptBreakpointType : XLineBreakpointType<XBreakpointProperties<*>>(BREAKPOINT_ID, BREAKPOINTS_TITLE) {

    /**
     * This has to be higher than the default priority (0) to avoid conflicts with other debuggers EmmyLua's
     */
    override fun getPriority(): Int = 100

    override fun createBreakpointProperties(file: VirtualFile, line: Int): XBreakpointProperties<*>? {
        return null
    }

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        if (DefoldScriptType.fromExtension(file.extension) != null) return true

        // Also allow .lua files, but only if it's a Defold project
        return project.getService().hasGameProjectFile() && file.extension == "lua"
    }

    override fun isSuspendThreadSupported(): Boolean = true
}

private const val BREAKPOINT_ID = "defold-script"
private const val BREAKPOINTS_TITLE = "Defold Line Breakpoints"
