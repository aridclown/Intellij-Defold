package com.aridclown.intellij.defold.debugger.lua

import org.luaj.vm2.Globals
import org.luaj.vm2.LoadState
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.*
import org.luaj.vm2.lib.jse.JseBaseLib
import org.luaj.vm2.lib.jse.JseMathLib
import org.luaj.vm2.lib.jse.LuajavaLib

/**
 * Creates a minimal, sandboxed LuaJ environment for safely evaluating
 * MobDebug-provided code dumps. Intentionally excludes I/O, and OS
 * libraries to avoid side effects or host access.
 */
object LuaSandbox {
    fun sandboxGlobals(): Globals = Globals().apply {
        load(JseBaseLib())
        load(PackageLib())
        load(Bit32Lib())
        load(TableLib())
        load(StringLib())
        load(CoroutineLib())
        load(JseMathLib())
        load(LuajavaLib())
        LoadState.install(this)
        LuaC.install(this)
    }
}

