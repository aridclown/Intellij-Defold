package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldScriptType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpointType

class DefoldScriptBreakpointType : XLineBreakpointType<XBreakpointProperties<*>>(BREAKPOINT_ID, BREAKPOINT_TITLE) {
    override fun createBreakpointProperties(file: VirtualFile, line: Int): XBreakpointProperties<*>? {
        return null
    }

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean =
        DefoldScriptType.fromExtension(file.extension ?: "") != null
}

private const val BREAKPOINT_ID = "defold-script"
private const val BREAKPOINT_TITLE = "Defold Script Breakpoint"
