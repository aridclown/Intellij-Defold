package com.aridclown.intellij.defold.process

import com.aridclown.intellij.defold.logging.DefoldLogClassifier
import com.aridclown.intellij.defold.logging.DefoldLogSeverity
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.util.Key

internal class DefoldProcessHandler(commandLine: GeneralCommandLine) : OSProcessHandler(commandLine) {
    private val buffer = StringBuilder()
    private var currentSeverity = DefoldLogSeverity.INFO

    override fun notifyTextAvailable(text: String, outputType: Key<*>) {
        feedChunk(text) { line ->
            currentSeverity = DefoldLogClassifier.detect(line, currentSeverity)
            super.notifyTextAvailable(line, currentSeverity.outputKey)
        }
    }

    override fun notifyProcessTerminated(exitCode: Int) {
        flushBuffer()
        super.notifyProcessTerminated(exitCode)
    }

    private fun feedChunk(chunk: String, emit: (String) -> Unit) {
        buffer.append(chunk)
        while (true) {
            val newline = buffer.indexOf('\n')
            if (newline == -1) break
            val line = buffer.substring(0, newline + 1)
            buffer.delete(0, newline + 1)
            emit(line)
        }
    }

    private fun flushBuffer() {
        if (buffer.isEmpty()) return
        val line = buffer.toString()
        buffer.setLength(0)
        currentSeverity = DefoldLogClassifier.detect(line, currentSeverity)
        super.notifyTextAvailable(line, currentSeverity.outputKey)
    }
}
