package com.aridclown.intellij.defold.debugger

// Handler interface for status-code strategies
fun interface MobDebugResponseHandlerStrategy {
    fun handle(raw: String, ctx: MobDebugProtocol.Ctx)
}

// Strategy Factory for creating and managing response strategies
object ResponseStrategyFactory {

    private val responseStrategies: Map<Int, MobDebugResponseHandlerStrategy> = mapOf(
        200 to OkMobDebugResponseHandlerStrategy(),
        202 to PausedMobDebugResponseHandlerStrategy(),
        203 to PausedWithWatchMobDebugResponseHandlerStrategy(),
        204 to OutputMobDebugResponseHandlerStrategy(),
        400 to BadRequestMobDebugResponseHandlerStrategy(),
        401 to ErrorMobDebugResponseHandlerStrategy()
    )

    // Optional: method to get a specific strategy
    fun getStrategy(statusCode: Int?): MobDebugResponseHandlerStrategy? =
        responseStrategies[statusCode]
}

// Individual strategy classes for better organization and testability
internal class OkMobDebugResponseHandlerStrategy : MobDebugResponseHandlerStrategy {
    override fun handle(raw: String, ctx: MobDebugProtocol.Ctx) {
        val message = raw.removePrefix("200 OK").trim().ifEmpty { null }
        val evt = Event.Ok(message)
        if (!ctx.completePendingWith(evt)) ctx.dispatch(evt)
    }
}

internal class PausedMobDebugResponseHandlerStrategy : MobDebugResponseHandlerStrategy {
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

internal class PausedWithWatchMobDebugResponseHandlerStrategy : MobDebugResponseHandlerStrategy {
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

internal class OutputMobDebugResponseHandlerStrategy : MobDebugResponseHandlerStrategy {
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

internal class ErrorMobDebugResponseHandlerStrategy : MobDebugResponseHandlerStrategy {
    override fun handle(raw: String, ctx: MobDebugProtocol.Ctx) {
        val lenStr = raw.removePrefix("401 Error in Execution ").trim()
        val len = lenStr.toIntOrNull() ?: 0
        ctx.awaitBody(len) { body ->
            val evt = Event.Error("Error in Execution", body)
            if (!ctx.completePendingWith(evt)) ctx.dispatch(evt)
        }
    }
}

internal class BadRequestMobDebugResponseHandlerStrategy : MobDebugResponseHandlerStrategy {
    override fun handle(raw: String, ctx: MobDebugProtocol.Ctx) {
        val rest = raw.removePrefix("400").trim()
        val message = if (rest.startsWith("Bad Request")) rest else $$"Bad Request $rest".trim()
        val evt = Event.Error("Bad Request", message.ifBlank { null })

        // Always dispatch for visibility, even if there is a pending callback.
        ctx.completePendingWith(evt)
        ctx.dispatch(evt)
    }
}
