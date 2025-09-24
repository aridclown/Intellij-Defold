package com.aridclown.intellij.defold.logging

import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.util.Key
import java.util.*

private const val DEFOLD_WARNING_KEY_ID = "defold.console.warning"
private const val DEFOLD_DEBUG_KEY_ID = "defold.console.debug"

internal object DefoldLogColorPalette {
    val warningKey: Key<String> = registerKey(DEFOLD_WARNING_KEY_ID, ConsoleViewContentType.LOG_INFO_OUTPUT)
    val debugKey: Key<String> = registerKey(DEFOLD_DEBUG_KEY_ID, ConsoleViewContentType.LOG_DEBUG_OUTPUT)

    private fun registerKey(id: String, type: ConsoleViewContentType): Key<String> = Key.create<String>(id).also {
        ConsoleViewContentType.registerNewConsoleViewType(it, type)
    }
}

internal enum class DefoldLogSeverity(val outputKey: Key<*>) {
    INFO(ProcessOutputType.STDOUT),
    WARNING(DefoldLogColorPalette.warningKey),
    ERROR(ProcessOutputType.STDERR),
    DEBUG(DefoldLogColorPalette.debugKey)
}

internal object DefoldLogClassifier {
    fun detect(line: String, previous: DefoldLogSeverity): DefoldLogSeverity {
        val trimmed = line.trimStart()
        if (trimmed.isEmpty()) return previous
        if (line.firstOrNull()?.isWhitespace() == true && !trimmed.startsWith("stack traceback:", ignoreCase = true)) {
            return previous
        }

        val prefix = trimmed.substringBefore(':').uppercase(Locale.US)
        val detected = when {
            prefix.startsWith("WARNING") -> DefoldLogSeverity.WARNING
            prefix.startsWith("ERROR") -> DefoldLogSeverity.ERROR
            prefix.startsWith("DEBUG") || prefix.startsWith("TRACE") -> DefoldLogSeverity.DEBUG
            prefix.startsWith("INFO") -> DefoldLogSeverity.INFO
            else -> null
        }

        return detected ?: previous
    }
}
