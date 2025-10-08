package com.aridclown.intellij.defold.util

import com.intellij.openapi.diagnostic.Logger

fun trySilently(block: () -> Unit) = try {
    block()
} catch (_: Throwable) {
}

fun tryWithWarning(logger: Logger, message: String, block: () -> Unit) = try {
    block()
} catch (e: Throwable) {
    logger.warn(message, e)
}

inline fun <R, U : R, T : R> T.letIf(condition: Boolean, block: (T) -> U): R = if (condition) block(this) else this
inline fun <R, U : R, T : R> T.letIfNot(condition: Boolean, block: (T) -> U): R = letIf(!condition, block)

/**
 * Checks whether the map has no blank keys or values.
 */
fun Map<String, String>.hasNoBlanks(): Boolean =
    haveNoBlanks(*keys.toTypedArray(), *values.toTypedArray())

private fun haveNoBlanks(vararg strings: String) = strings.none(String::isNullOrBlank)
