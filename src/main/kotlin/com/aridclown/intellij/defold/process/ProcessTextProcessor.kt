package com.aridclown.intellij.defold.process

import com.aridclown.intellij.defold.logging.LogClassifier
import com.aridclown.intellij.defold.logging.LogSeverity

/**
 * Handles buffering and severity detection for process output.
 * Extracts the common logic duplicated between DefoldProcessHandler and tests.
 */
internal class ProcessTextProcessor {
    private val buffer = StringBuilder()
    private var currentSeverity = LogSeverity.INFO

    /**
     * Process a chunk of text, emitting complete lines via the callback.
     * Incomplete lines are buffered until a newline arrives.
     */
    fun processChunk(chunk: String, emit: (String, LogSeverity) -> Unit) {
        buffer.append(chunk)
        generateSequence { buffer.indexOf('\n').takeIf { it != -1 } }
            .forEach {
                val line = buffer.substring(0, it + 1)
                buffer.delete(0, it + 1)
                currentSeverity = LogClassifier.detect(line, currentSeverity)
                emit(line, currentSeverity)
            }
    }

    /**
     * Flush any remaining buffered text when the process terminates.
     */
    fun flush(emit: (String, LogSeverity) -> Unit) {
        if (buffer.isNotEmpty()) {
            val line = buffer.toString()
            buffer.setLength(0)
            currentSeverity = LogClassifier.detect(line, currentSeverity)
            emit(line, currentSeverity)
        }
    }
}

