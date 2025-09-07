package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.MobDebugProtocol.CommandType
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

sealed class Event {
    data class Paused(val file: String, val line: Int, val watchIndex: Int? = null) : Event()
    data class Ok(val message: String?) : Event()
    data class Error(val message: String, val details: String?) : Event()
    data class Output(val stream: String, val text: String) : Event()
    data class Unknown(val line: String) : Event()
}

data class Pending(val type: CommandType, val callback: (Event) -> Unit)

// State machine for consuming a payload with declared length (e.g., 204 Output, 401 Error)
class AwaitingBody(
    private val expectedLen: Int,
    private val onComplete: (String) -> Unit
) {
    private val outputStream = ByteArrayOutputStream()

    fun consumeLine(line: String): Boolean {
        val bytes = (line + "\n").toByteArray(StandardCharsets.UTF_8)
        outputStream.write(bytes)
        return outputStream.size() >= expectedLen
    }

    fun complete() {
        val all = outputStream.toByteArray()
        val slice = if (all.size >= expectedLen) all.copyOfRange(0, expectedLen) else all
        onComplete(String(slice, StandardCharsets.UTF_8))
    }
}