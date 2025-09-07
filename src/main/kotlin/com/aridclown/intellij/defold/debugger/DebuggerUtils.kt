package com.aridclown.intellij.defold.debugger

import java.util.concurrent.ConcurrentHashMap

fun ConcurrentHashMap.KeySetView<BreakpointLocation, Boolean>.add(path: String, line: Int) =
    add(BreakpointLocation(path, line))

fun ConcurrentHashMap.KeySetView<BreakpointLocation, Boolean>.contains(path: String, line: Int) =
    contains(BreakpointLocation(path, line))

fun ConcurrentHashMap.KeySetView<BreakpointLocation, Boolean>.remove(path: String, line: Int) =
    remove(BreakpointLocation(path, line))