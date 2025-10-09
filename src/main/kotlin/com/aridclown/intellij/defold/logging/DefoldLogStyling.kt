package com.aridclown.intellij.defold.logging

import com.aridclown.intellij.defold.logging.DefoldLogSeverity.*
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewContentType.*
import com.intellij.openapi.util.Key
import java.util.*

private const val DEFOLD_WARNING_KEY_ID = "defold.console.warning"
private const val DEFOLD_DEBUG_KEY_ID = "defold.console.debug"
private const val DEFOLD_RESOURCE_KEY_ID = "defold.console.resource"

internal object DefoldLogColorPalette {
    val warningKey: Key<String> = registerKey(DEFOLD_WARNING_KEY_ID, LOG_INFO_OUTPUT)
    val debugKey: Key<String> = registerKey(DEFOLD_DEBUG_KEY_ID, LOG_DEBUG_OUTPUT)
    val resourceKey: Key<String> = registerKey(DEFOLD_RESOURCE_KEY_ID, USER_INPUT)

    private fun registerKey(id: String, type: ConsoleViewContentType): Key<String> = Key.create<String>(id).also {
        registerNewConsoleViewType(it, type)
    }
}

internal enum class DefoldLogSeverity(val outputKey: Key<*>) {
    INFO(ProcessOutputType.STDOUT),
    WARNING(DefoldLogColorPalette.warningKey),
    ERROR(ProcessOutputType.STDERR),
    DEBUG(DefoldLogColorPalette.debugKey),
    RESOURCE(DefoldLogColorPalette.resourceKey)
}

internal object DefoldLogClassifier {
    fun detect(line: String, previous: DefoldLogSeverity): DefoldLogSeverity {
        val trimmed = line.trimStart()
        if (trimmed.isEmpty()) return previous
        if (line.firstOrNull()?.isWhitespace() == true && !trimmed.startsWith("stack traceback:", ignoreCase = true)) {
            return previous
        }

        val uppercase = trimmed.uppercase(Locale.US)
        val detected = when {
            uppercase.startsWith("WARNING") -> WARNING
            uppercase.startsWith("ERROR") -> ERROR
            uppercase.startsWith("DEBUG") || uppercase.startsWith("TRACE") -> DEBUG
            uppercase.startsWith("INFO") -> when {
                uppercase.contains("RESOURCE") -> RESOURCE
                else -> INFO
            }

            else -> null
        }

        return detected ?: previous
    }
}
