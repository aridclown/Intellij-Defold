package com.aridclown.intellij.defold.debugger

/**
 * Immutable key for tracking remote breakpoint locations (path + line).
 */
data class BreakpointLocation(val path: String, val line: Int)