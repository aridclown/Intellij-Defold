package com.aridclown.intellij.defold.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.util.Key
import java.util.*

private const val DEFOLD_WARNING_KEY_ID = "defold.console.warning"
private const val DEFOLD_DEBUG_KEY_ID = "defold.console.debug"

internal class DefoldProcessHandler(commandLine: GeneralCommandLine) : OSProcessHandler(commandLine) {
    private val buffer = StringBuilder()
    private var currentSeverity = Severity.INFO

    override fun notifyTextAvailable(text: String, outputType: Key<*>) {
        feedChunk(text) { line ->
            val severity = classify(line)?.also { currentSeverity = it } ?: currentSeverity
            super.notifyTextAvailable(line, severity.outputKey)
        }
    }

    override fun notifyProcessTerminated(exitCode: Int) {
        flushBuffer()
        super.notifyProcessTerminated(exitCode)
    }

    private fun feedChunk(chunk: String, emit: (String) -> Unit) {
        buffer.append(chunk)
        while (true) {
            val newline = buffer.indexOf("\n")
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
        val severity = classify(line)?.also { currentSeverity = it } ?: currentSeverity
        super.notifyTextAvailable(line, severity.outputKey)
    }

    private fun classify(line: String): Severity? {
        val trimmed = line.trimStart()
        if (trimmed.isEmpty()) return null
        if (line.firstOrNull()?.isWhitespace() == true) return null

        val upperPrefix = trimmed.substringBefore(':').uppercase(Locale.US)
        return when {
            trimmed.startsWith("stack traceback:", ignoreCase = true) -> Severity.ERROR
            trimmed.startsWith("  ") -> null // continuation (stack traces etc.)
            upperPrefix.startsWith("INFO") -> Severity.INFO
            upperPrefix.startsWith("WARNING") -> Severity.WARNING
            upperPrefix.startsWith("ERROR") -> Severity.ERROR
            upperPrefix.startsWith("DEBUG") || upperPrefix.startsWith("TRACE") -> Severity.DEBUG
            else -> null
        }
    }

    private enum class Severity(val outputKey: Key<*>) {
        INFO(ProcessOutputType.STDOUT),
        ERROR(ProcessOutputType.STDERR),
        WARNING(WARNING_OUTPUT_KEY),
        DEBUG(DEBUG_OUTPUT_KEY)
    }

    companion object {
        private val WARNING_OUTPUT_KEY = registerKey(DEFOLD_WARNING_KEY_ID, ConsoleViewContentType.LOG_INFO_OUTPUT)
        private val DEBUG_OUTPUT_KEY = registerKey(DEFOLD_DEBUG_KEY_ID, ConsoleViewContentType.LOG_DEBUG_OUTPUT)

        private fun registerKey(id: String, type: ConsoleViewContentType): Key<String> = Key.create<String>(id).also {
            ConsoleViewContentType.registerNewConsoleViewType(it, type)
        }
    }
}
