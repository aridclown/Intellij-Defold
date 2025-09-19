package com.aridclown.intellij.defold.debugger

/** Handler interface for status-code strategies. */
fun interface ResponseHandler {
    fun handle(raw: String, ctx: MobDebugProtocol.Ctx)
}

/** Registry for response handlers keyed by status code. */
object MobDebugResponseHandler {
    private val handlers: Map<Int, ResponseHandler> = mapOf(
        200 to OkResponseHandler(),
        202 to PausedResponseHandler(),
        203 to PausedWatchResponseHandler(),
        204 to OutputResponseHandler(),
        400 to BadRequestResponseHandler(),
        401 to ErrorResponseHandler()
    )

    fun getStrategy(statusCode: Int?): ResponseHandler? = handlers[statusCode]
}

// Individual handlers for better organization and testability
internal class OkResponseHandler : ResponseHandler {
    override fun handle(raw: String, ctx: MobDebugProtocol.Ctx) {
        val rest = raw.removePrefix("200 OK").trim()

        // Special-case EXEC, which reports length followed by a body line(s)
        when (ctx.peekPendingType()) {
            MobDebugProtocol.CommandType.EXEC,
            MobDebugProtocol.CommandType.STACK -> {
                if (rest.isEmpty()) {
                    // Fire-and-forget command response; actual payload will follow for EXEC/STACK.
                    return
                }
                val len = rest.toIntOrNull()
                if (len != null) {
                    ctx.awaitBody(len) { body ->
                        val evt = Event.Ok(body)
                        if (!ctx.completePendingWith(evt)) ctx.dispatch(evt)
                    }
                } else {
                    val evt = Event.Ok(rest.ifEmpty { null })
                    if (!ctx.completePendingWith(evt)) ctx.dispatch(evt)
                }
            }

            else -> {
                // Default: complete pending (e.g., RUN/OVER)
                val evt = Event.Ok(rest.ifEmpty { null })
                if (!ctx.completePendingWith(evt)) ctx.dispatch(evt)
            }
        }
    }
}

internal class PausedResponseHandler : ResponseHandler {
    override fun handle(raw: String, ctx: MobDebugProtocol.Ctx) {
        val rest = raw.removePrefix("202 Paused ")
        val parts = rest.split(' ')
        if (parts.size >= 2) {
            val file = parts[0]
            val line = parts[1].toIntOrNull() ?: 0
            ctx.dispatch(Event.Paused(file, line, null))
        } else {
            ctx.dispatch(Event.Unknown(raw))
        }
    }
}

internal class PausedWatchResponseHandler : ResponseHandler {
    override fun handle(raw: String, ctx: MobDebugProtocol.Ctx) {
        val rest = raw.removePrefix("203 Paused ")
        val parts = rest.split(' ')
        if (parts.size >= 3) {
            val file = parts[0]
            val line = parts[1].toIntOrNull() ?: 0
            val idx = parts[2].toIntOrNull()
            ctx.dispatch(Event.Paused(file, line, idx))
        } else {
            ctx.dispatch(Event.Unknown(raw))
        }
    }
}

internal class OutputResponseHandler : ResponseHandler {
    override fun handle(raw: String, ctx: MobDebugProtocol.Ctx) {
        val rest = raw.removePrefix("204 Output ")
        val parts = rest.split(' ')
        if (parts.size >= 2) {
            val stream = parts[0]
            val len = parts[1].toIntOrNull() ?: 0
            ctx.awaitBody(len) { body ->
                ctx.dispatch(Event.Output(stream, body))
            }
        } else {
            ctx.dispatch(Event.Unknown(raw))
        }
    }
}

internal class ErrorResponseHandler : ResponseHandler {
    override fun handle(raw: String, ctx: MobDebugProtocol.Ctx) {
        val (message, len) = parseErrorHeader(raw)
        ctx.awaitBody(len) { body ->
            val evt = Event.Error(message, body)
            if (!ctx.completePendingWith(evt)) ctx.dispatch(evt)
        }
    }

    fun parseErrorHeader(raw: String): Pair<String, Int> {
        val trimmed = raw.removePrefix("401").trim()
        val length = trimmed.takeLastWhile { it.isDigit() }.toIntOrNull() ?: 0
        val message = trimmed.dropLastWhile { it.isDigit() }.trimEnd().ifBlank { "Error" }
        return message to length
    }
}

internal class BadRequestResponseHandler : ResponseHandler {
    override fun handle(raw: String, ctx: MobDebugProtocol.Ctx) {
        val rest = raw.removePrefix("400").trim()
        val message = if (rest.startsWith("Bad Request")) rest else "Bad Request $rest".trim()
        val evt = Event.Error("Bad Request", message.ifBlank { null })

        // Always dispatch for visibility, even if there is a pending callback.
        ctx.completePendingWith(evt)
        ctx.dispatch(evt)
    }
}
