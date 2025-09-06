package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.MobDebugProtocol.CommandType.*
import com.intellij.openapi.diagnostic.Logger
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lightweight MobDebug protocol parser and dispatcher.
 * Handles single-line status responses and multi-line payloads (by length).
 */
class MobDebugProtocol(
    private val server: MobDebugServer,
    private val logger: Logger
) {

    enum class CommandType {
        RUN, STEP, OVER, OUT, SUSPEND, SETB, DELB, STACK, EXEC, OUTPUT, BASEDIR, EXIT
    }

    sealed class Event {
        data class Paused(val file: String, val line: Int, val watchIndex: Int? = null) : Event()
        data class Ok(val message: String?) : Event()
        data class Error(val message: String, val details: String?) : Event()
        data class Output(val stream: String, val text: String) : Event()
        data class Unknown(val line: String) : Event()
    }

    private data class Pending(val type: CommandType, val callback: (Event) -> Unit)

    private val pendingQueue: ConcurrentLinkedQueue<Pending> = ConcurrentLinkedQueue()

    // State machine for consuming a payload with declared length (e.g., 204 Output, 401 Error)
    private class AwaitingBody(
        private val expectedLen: Int,
        private val onComplete: (String) -> Unit
    ) {
        private val outputStream = ByteArrayOutputStream()

        fun consumeLine(line: String): Boolean {
            val bytes = (line + "\n").toByteArray(UTF_8)
            outputStream.write(bytes)
            return outputStream.size() >= expectedLen
        }

        fun complete() {
            val all = outputStream.toByteArray()
            val slice = if (all.size >= expectedLen) all.copyOfRange(0, expectedLen) else all
            onComplete(String(slice, UTF_8))
        }
    }

    @Volatile
    private var awaiting: AwaitingBody? = null

    // External listeners for high-level events
    private val listeners = CopyOnWriteArrayList<(Event) -> Unit>()

    init {
        server.addListener { line -> onLine(line) }
    }

    fun addListener(listener: (Event) -> Unit) {
        listeners.add(listener)
    }

    // ---- Public typed commands ---------------------------------------------------------------

    fun run(onResult: (Event) -> Unit = { }) = send(RUN, onResult)
    fun step(onResult: (Event) -> Unit = { }) = send(STEP, onResult)
    fun over(onResult: (Event) -> Unit = { }) = send(OVER, onResult)
    fun out(onResult: (Event) -> Unit = { }) = send(OUT, onResult)
    fun suspend(onResult: (Event) -> Unit = { }) = send(SUSPEND, onResult)
    fun exit(onResult: (Event) -> Unit = { }) = send(EXIT, onResult)

    fun setBreakpoint(remotePath: String, line: Int, onResult: (Event) -> Unit = { }) =
        sendRaw(SETB, "SETB $remotePath $line", onResult)

    fun deleteBreakpoint(remotePath: String, line: Int, onResult: (Event) -> Unit = { }) =
        sendRaw(DELB, "DELB $remotePath $line", onResult)

    fun basedir(dir: String, onResult: (Event) -> Unit = { }) =
        sendRaw(BASEDIR, "BASEDIR $dir", onResult)

    fun outputStdout(mode: Char, onResult: (Event) -> Unit = { }) =
        sendRaw(OUTPUT, "OUTPUT stdout $mode", onResult)

    fun clearAllBreakpoints(onResult: (Event) -> Unit = { }) =
        sendRaw(DELB, "DELB * 0", onResult)

    // Future slices: STACK/EXEC helpers will be added here and parsed by onLine()

    // ---- Internal ---------------------------------------------------------------------------

    private fun send(type: CommandType, onResult: (Event) -> Unit) {
        pendingQueue.add(Pending(type, onResult))
        server.send(type.name)
    }

    private fun sendRaw(type: CommandType, command: String, onResult: (Event) -> Unit) {
        pendingQueue.add(Pending(type, onResult))
        server.send(command)
    }

    // ---- Strategy plumbing -----------------------------------------------------------------

    // Strategy context passed to handlers for shared behaviors
    inner class Ctx {
        fun dispatch(event: Event) = this@MobDebugProtocol.dispatch(event)
        fun completePendingWith(event: Event): Boolean = this@MobDebugProtocol.completePendingWith(event)
        fun awaitBody(expectedLen: Int, onComplete: (String) -> Unit) {
            awaiting = AwaitingBody(expectedLen, onComplete)
        }
    }

    private val ctx = Ctx()

    private fun onLine(raw: String) {
        logger.info("[proto] <= $raw")

        // If we are in the middle of reading a payload by length, keep consuming until done
        val awaitingNow = awaiting
        if (awaitingNow != null) {
            val done = awaitingNow.consumeLine(raw)
            if (done) {
                try {
                    awaitingNow.complete()
                } finally {
                    awaiting = null
                }
            }
            return
        }

        // Determine numeric status code and route to strategy
        val code = raw.take(3).toIntOrNull()
        val strategy = ResponseStrategyFactory.getStrategy(code)
        when {
            strategy == null -> dispatch(Event.Unknown(raw))
            else -> strategy.handle(raw, ctx)
        }
    }

    private fun completePendingWith(event: Event): Boolean {
        val p = pendingQueue.poll() ?: return false
        try {
            p.callback(event)
        } catch (t: Throwable) {
            logger.warn("[proto] pending callback failed", t)
        }
        return true
    }

    private fun dispatch(event: Event) = listeners.forEach { listener ->
        try {
            listener(event)
        } catch (t: Throwable) {
            logger.warn("[proto] listener failed", t)
        }
    }
}
