package com.aridclown.intellij.defold.util

import com.intellij.openapi.diagnostic.Logger

fun tryOrFalse(block: () -> Boolean): Boolean = try {
    block()
} catch (_: Throwable) {
    false
}

fun trySilently(block: () -> Unit) = try {
    block()
} catch (_: Throwable) {
}

fun tryWithWarning(logger: Logger, message: String, block: () -> Unit) = try {
    block()
} catch (e: Throwable) {
    logger.warn(message, e)
}

/**
 * Checks whether the map has no blank keys or values.
 */
fun Map<String, String>.hasNoBlanks(): Boolean =
    haveNoBlanks(*keys.toTypedArray(), *values.toTypedArray())

private fun haveNoBlanks(vararg strings: String) = strings.none(String::isNullOrBlank)
