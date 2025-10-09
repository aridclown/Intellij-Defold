package com.aridclown.intellij.defold.console

import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Coalesces repeated console lines by rewriting the last line with a counter instead of printing duplicates.
 * Reusable across run and debug consoles by supplying a suitable printer function.
 */
class ConsoleOutputAggregator<T>(
    private val printer: (String, T) -> Unit,
    private val counterPrinter: ((String, T) -> Unit)? = null,
    private val counterTypeProvider: ((T) -> T)? = null,
    private val flushDelayMillis: Long = 250
) {
    private val scheduler = AppExecutorUtil.getAppScheduledExecutorService()

    @Volatile private var lastMessage: String? = null
    @Volatile private var lastType: T? = null
    @Volatile private var repeatCount: Int = 0
    @Volatile private var lastDisplayedLength: Int = 0
    @Volatile private var pendingNewline: Boolean = false
    @Volatile private var flushFuture: ScheduledFuture<*>? = null

    @Synchronized
    fun append(rawText: String, type: T) {
        splitSegments(rawText).forEach { segment ->
            appendSegment(segment, type)
        }
    }

    @Synchronized
    fun finalizeAndReset() {
        finalizeLine()
        resetState()
    }

    private fun appendSegment(segment: Segment, type: T) {
        val message = segment.text.trimEnd('\r', '\n')

        if (message.isEmpty()) {
            finalizeLine(type)
            printer(segment.text, type)
            resetState()
            return
        }

        val isSameMessage = message == lastMessage && type == lastType
        if (!isSameMessage) {
            finalizeLine()
            lastMessage = message
            lastType = type
            repeatCount = 0
            lastDisplayedLength = 0
        }

        repeatCount += 1
        renderCurrentLine(type)

        pendingNewline = segment.endsWithTerminator
        if (pendingNewline) scheduleFlush() else cancelFlush()
    }

    private fun renderCurrentLine(type: T) {
        val message = lastMessage ?: return
        val counterPrefix = if (repeatCount > 1) "[$repeatCount] " else ""
        val display = counterPrefix + message
        val padded = if (display.length < lastDisplayedLength) {
            display + " ".repeat(lastDisplayedLength - display.length)
        } else display
        val carriage = if (lastDisplayedLength == 0) "" else "\r"

        if (repeatCount > 1) {
            val counterType = counterTypeProvider?.invoke(type) ?: type
            val counterPrinterFn = counterPrinter ?: printer
            val rest = padded.removePrefix(counterPrefix)
            counterPrinterFn(carriage + counterPrefix, counterType)
            if (rest.isNotEmpty()) {
                printer(rest, type)
            }
        } else {
            printer(carriage + padded, type)
        }

        lastDisplayedLength = display.length
    }

    private fun finalizeLine(fallbackType: T? = lastType) {
        flushFuture?.cancel(false)
        flushFuture = null

        if (pendingNewline && fallbackType != null && lastDisplayedLength > 0) {
            printer("\n", fallbackType)
        }

        pendingNewline = false
        lastDisplayedLength = 0
    }

    private fun scheduleFlush() {
        flushFuture?.cancel(false)
        flushFuture = scheduler.schedule({
            synchronized(this) {
                finalizeLine()
                resetState()
            }
        }, flushDelayMillis, TimeUnit.MILLISECONDS)
    }

    private fun cancelFlush() {
        flushFuture?.cancel(false)
        flushFuture = null
    }

    private fun resetState() {
        lastMessage = null
        lastType = null
        repeatCount = 0
        lastDisplayedLength = 0
        pendingNewline = false
    }

    private data class Segment(val text: String, val endsWithTerminator: Boolean)

    companion object {
        private fun splitSegments(text: String): List<Segment> {
            if (text.isEmpty()) return listOf(Segment(text, false))

            val result = mutableListOf<Segment>()
            val builder = StringBuilder()
            var index = 0
            while (index < text.length) {
                val ch = text[index]
                builder.append(ch)
                if (ch == '\n' || ch == '\r') {
                    if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') {
                        index += 1
                        builder.append('\n')
                    }
                    result += Segment(builder.toString(), true)
                    builder.setLength(0)
                }
                index += 1
            }

            if (builder.isNotEmpty()) {
                result += Segment(builder.toString(), false)
            }

            return result
        }
    }
}
