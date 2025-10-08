package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.MobDebugProtocol.CommandType

sealed class Event {
    data class Paused(val file: String, val line: Int, val watchIndex: Int? = null) : Event()
    data class Ok(val message: String?) : Event()
    data class Error(val message: String, val details: String?) : Event()
    data class Output(val stream: String, val text: String) : Event()
    data class Unknown(val line: String) : Event()
}

data class Pending(val type: CommandType, val callback: (Event) -> Unit)