package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.MobDebugProtocol.CommandType.*
import com.aridclown.intellij.defold.util.trySilently
import com.aridclown.intellij.defold.util.tryWithWarning
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit.MILLISECONDS

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

    private val pendingQueue: ConcurrentLinkedQueue<Pending> = ConcurrentLinkedQueue()

    // Simple in-flight timeout management; MobDebug is single-flight by design
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "MobDebugProtocolTimer").apply { isDaemon = true }
    }

    @Volatile
    private var currentTimeout: ScheduledFuture<*>? = null
    private val defaultTimeoutMs = 7_000L

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
        sendRaw(SETB, "SETB $remotePath $line", onResult = onResult)

    fun deleteBreakpoint(remotePath: String, line: Int, onResult: (Event) -> Unit = { }) =
        sendRaw(DELB, "DELB $remotePath $line", onResult = onResult)

    fun basedir(dir: String, onResult: (Event) -> Unit = { }) =
        sendRaw(BASEDIR, "BASEDIR $dir", onResult = onResult)

    fun outputStdout(mode: Char, onResult: (Event) -> Unit = { }) =
        sendRaw(OUTPUT, "OUTPUT stdout $mode", onResult = onResult)

    fun clearAllBreakpoints(onResult: (Event) -> Unit = { }) =
        sendRaw(DELB, "DELB * 0", onResult = onResult)

    /**
     * STACK: Returns a serialized dump of stack frames (MobDebug serpent format)
     */
    fun stack(options: String? = null, onResult: (String) -> Unit, onError: (Event.Error) -> Unit = { }) {
        val suffix = when {
            options.isNullOrBlank() -> ""
            else -> " -- ${options.trim()}"
        }

        sendRaw(STACK, "STACK$suffix", expectResponse = true) { evt ->
            when (evt) {
                is Event.Ok -> onResult(evt.message.orEmpty())
                is Event.Error -> onError(evt)
                else -> logger.warn("Unexpected STACK response: $evt")
            }
        }
    }

    /**
     * EXEC: Runs 'chunk' in target; when 'frame' is provided, executed in that frame
     */
    fun exec(
        chunk: String,
        frame: Int? = null,
        options: String? = null,
        onResult: (String) -> Unit,
        onError: (Event.Error) -> Unit = { }
    ) {
        val params = buildString {
            if (frame != null || !options.isNullOrBlank()) {
                append(" -- { ")
                if (frame != null) append("stack = ").append(frame)
                if (!options.isNullOrBlank()) {
                    if (isNotEmpty()) append(", ")
                    append(options.trim().trim('{', '}', ' '))
                }
                append(" }")
            }
        }

        // chunk may contain newlines; protocol handles length-prefixed body
        sendRaw(EXEC, "EXEC $chunk$params", expectResponse = true) { evt ->
            when (evt) {
                is Event.Ok -> onResult(evt.message.orEmpty())
                is Event.Error -> onError(evt)
                else -> logger.warn("Unexpected EXEC response: $evt")
            }
        }
    }

    // ---- Internal ---------------------------------------------------------------------------

    private fun send(type: CommandType, onResult: (Event) -> Unit) {
        enqueue(type, onResult)
        server.send(type.name)
        scheduleTimeoutForHead()
    }

    private fun sendRaw(
        type: CommandType,
        command: String,
        expectResponse: Boolean = false,
        onResult: (Event) -> Unit,
    ) {
        if (expectResponse) {
            enqueue(type, onResult)
        }
        server.send(command)
        if (expectResponse) {
            scheduleTimeoutForHead()
        }
    }

    private fun enqueue(type: CommandType, onResult: (Event) -> Unit) {
        pendingQueue.add(Pending(type, onResult))
    }

    // ---- Strategy plumbing -----------------------------------------------------------------

    // Strategy context passed to handlers for shared behaviors
    inner class Ctx {
        fun dispatch(event: Event) = this@MobDebugProtocol.dispatch(event)
        fun completePendingWith(event: Event): Boolean = this@MobDebugProtocol.completePendingWith(event)
        fun peekPendingType(): CommandType? = pendingQueue.peek()?.type
        fun awaitBody(expectedLen: Int, onComplete: (String) -> Unit) {
            server.requestBody(expectedLen) { body ->
                tryWithWarning(logger, "[proto] body callback failed") {
                    onComplete(body)
                }
            }
        }
    }

    private val ctx = Ctx()

    private fun onLine(raw: String) {
        // Determine numeric status code and route to strategy
        val code = raw.take(3).toIntOrNull()
        val strategy = MobDebugResponseHandler.getStrategy(code)
        when {
            strategy == null -> dispatch(Event.Unknown(raw))
            else -> strategy.handle(raw, ctx)
        }
    }

    private fun completePendingWith(event: Event): Boolean {
        val pending = pendingQueue.poll() ?: return false
        tryWithWarning(logger, "[proto] pending callback failed") {
            pending.callback(event)
        }

        // cancel this request timeout and arm timeout for the next head (if any)
        trySilently {
            currentTimeout?.cancel(true)
        }
        scheduleTimeoutForHead()
        return true
    }

    private fun dispatch(event: Event) = listeners.forEach { listener ->
        tryWithWarning(logger, "[proto] listener failed") {
            listener(event)
        }
    }

    private fun scheduleTimeoutForHead() {
        // Only one in-flight command at a time; head represents in-flight
        if (pendingQueue.peek() == null || currentTimeout?.isDone == false) return

        // Longer timeout for EXEC/STACK as they may move more data
        val headType = pendingQueue.peek()?.type
        val timeoutMs = when (headType) {
            EXEC, STACK -> 10_000L // 10 seconds
            EXIT -> return // No timeout for EXIT
            else -> defaultTimeoutMs
        }

        currentTimeout = scheduler.schedule({
            val head = pendingQueue.peek() ?: return@schedule

            // Remove head if still the same (best-effort)
            pendingQueue.poll()
            dispatch(Event.Error("Timeout", "${head.type} timed out after ${timeoutMs}ms"))

            // Arm next head if any
            scheduleTimeoutForHead()
        }, timeoutMs, MILLISECONDS)
    }
}
