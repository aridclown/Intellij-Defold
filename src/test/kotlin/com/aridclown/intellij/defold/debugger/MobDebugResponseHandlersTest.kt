package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.MobDebugProtocol.CommandType.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class MobDebugResponseHandlerTest {
    @ParameterizedTest
    @MethodSource("statusToHandlerMappings")
    fun `all standard status codes have handlers`(
        statusCode: Int,
        handlerClass: Class<out ResponseHandler>
    ) {
        assertThat(MobDebugResponseHandler.getStrategy(statusCode))
            .isNotNull
            .isInstanceOf(handlerClass)
    }

    @Test
    fun `returns no strategy when unknown status code`() {
        assertThat(MobDebugResponseHandler.getStrategy(999)).isNull()
    }

    companion object {
        @JvmStatic
        fun statusToHandlerMappings() = listOf(
            arguments(200, OkResponseHandler::class.java),
            arguments(202, PausedResponseHandler::class.java),
            arguments(203, PausedWatchResponseHandler::class.java),
            arguments(204, OutputResponseHandler::class.java),
            arguments(400, BadRequestResponseHandler::class.java),
            arguments(401, ErrorResponseHandler::class.java)
        )
    }
}

class OkResponseHandlerTest {
    private val handler = OkResponseHandler()
    private val ctx = relaxedCtx()

    @Test
    fun `handles simple OK response with no payload`() {
        every { ctx.peekPendingType() } returns RUN
        every { ctx.completePendingWith(any()) } returns true

        handler.handle("200 OK", ctx)

        verify {
            ctx.completePendingWith(match(::ok))
        }
    }

    @Test
    fun `handles OK response with payload for non-EXEC command`() {
        every { ctx.peekPendingType() } returns STEP
        every { ctx.completePendingWith(any()) } returns true

        handler.handle("200 OK some data", ctx)

        verify {
            ctx.completePendingWith(match { ok(it, "some data") })
        }
    }

    @Test
    fun `handles EXEC command with length indicator`() {
        every { ctx.peekPendingType() } returns EXEC
        ctx.stubBody(42, "test body content")

        handler.handle("200 OK 42", ctx)

        verify { ctx.awaitBody(42, any()) }
        verify { ctx.completePendingWith(match { ok(it, "test body content") }) }
    }

    @Test
    fun `handles STACK command with length indicator`() {
        every { ctx.peekPendingType() } returns STACK
        ctx.stubBody(100, "stack trace")

        handler.handle("200 OK 100", ctx)

        verify { ctx.awaitBody(100, any()) }
        verify { ctx.completePendingWith(match { ok(it, "stack trace") }) }
    }

    @Test
    fun `handles EXEC with empty response for fire-and-forget`() {
        every { ctx.peekPendingType() } returns EXEC

        handler.handle("200 OK", ctx)

        verify(exactly = 0) { ctx.awaitBody(any(), any()) }
        verify(exactly = 0) { ctx.completePendingWith(any()) }
    }

    @Test
    fun `handles EXEC with non-numeric length as regular payload`() {
        every { ctx.peekPendingType() } returns EXEC
        every { ctx.completePendingWith(any()) } returns true

        handler.handle("200 OK invalid", ctx)

        verify { ctx.completePendingWith(match { ok(it, "invalid") }) }
    }

    @Test
    fun `dispatches event when completePendingWith returns false`() {
        every { ctx.peekPendingType() } returns RUN
        every { ctx.completePendingWith(any()) } returns false

        handler.handle("200 OK", ctx)

        verify { ctx.dispatch(match(::ok)) }
    }
}

class PausedResponseHandlerTest {
    private val handler = PausedResponseHandler()
    private val ctx = relaxedCtx()

    @Test
    fun `parses paused response with file and line`() {
        handler.handle("202 Paused /path/to/file.lua 42", ctx)

        verify { ctx.dispatch(match { paused(it, file = "/path/to/file.lua", line = 42) }) }
    }

    @Test
    fun `handles paused response with invalid line number`() {
        handler.handle("202 Paused /path/to/file.lua invalid", ctx)

        verify { ctx.dispatch(match { paused(it, file = "/path/to/file.lua", line = 0) }) }
    }

    @Test
    fun `dispatches Unknown event for malformed paused response`() {
        handler.handle("202 Paused incomplete", ctx)

        verify { ctx.dispatch(match(::unknown)) }
    }

    @Test
    fun `handles paused response with extra parts`() {
        handler.handle("202 Paused /file.lua 10 extra data", ctx)

        verify { ctx.dispatch(match { paused(it, file = "/file.lua", line = 10) }) }
    }
}

class PausedWatchResponseHandlerTest {
    private val handler = PausedWatchResponseHandler()
    private val ctx = relaxedCtx()

    @Test
    fun `parses paused watch response with file, line, and index`() {
        handler.handle("203 Paused /path/to/file.lua 42 5", ctx)

        verify { ctx.dispatch(match { paused(it, file = "/path/to/file.lua", line = 42, watchIndex = 5) }) }
    }

    @Test
    fun `handles invalid line number in watch response`() {
        handler.handle("203 Paused /file.lua invalid 3", ctx)

        verify { ctx.dispatch(match { paused(it, file = "/file.lua", line = 0, watchIndex = 3) }) }
    }

    @Test
    fun `handles invalid watch index`() {
        handler.handle("203 Paused /file.lua 10 invalid", ctx)

        verify { ctx.dispatch(match { paused(it, file = "/file.lua", line = 10, watchIndex = null) }) }
    }

    @Test
    fun `dispatches Unknown event for incomplete watch response`() {
        handler.handle("203 Paused /file.lua 10", ctx)

        verify { ctx.dispatch(match(::unknown)) }
    }

    @Test
    fun `dispatches Unknown event for malformed watch response`() {
        handler.handle("203 Paused", ctx)

        verify { ctx.dispatch(match(::unknown)) }
    }
}

class OutputResponseHandlerTest {
    private val handler = OutputResponseHandler()
    private val ctx = relaxedCtx()

    @Test
    fun `parses output response and awaits body`() {
        ctx.stubBody(25, "output message content")
        handler.handle("204 Output stdout 25", ctx)

        verify { ctx.awaitBody(25, any()) }
        verify { ctx.dispatch(match { output(it, stream = "stdout", text = "output message content") }) }
    }

    @Test
    fun `handles stderr stream`() {
        ctx.stubBody(10, "error text")
        handler.handle("204 Output stderr 10", ctx)

        verify { ctx.dispatch(match { output(it, stream = "stderr", text = "error text") }) }
    }

    @Test
    fun `handles zero-length output`() {
        ctx.stubBody(0, "")
        handler.handle("204 Output stdout 0", ctx)

        verify { ctx.awaitBody(0, any()) }
    }

    @Test
    fun `dispatches Unknown event for malformed output response`() {
        handler.handle("204 Output incomplete", ctx)

        verify { ctx.dispatch(match(::unknown)) }
    }

    @Test
    fun `handles invalid length as zero`() {
        ctx.stubBody(0, "")
        handler.handle("204 Output stdout invalid", ctx)

        verify { ctx.awaitBody(0, any()) }
    }
}

class ErrorResponseHandlerTest {
    private val handler = ErrorResponseHandler()
    private val ctx = relaxedCtx()

    @Test
    fun `parses error response and awaits body`() {
        ctx.stubBody(50, "detailed error message")
        handler.handle("401 Runtime Error 50", ctx)

        verify { ctx.awaitBody(50, any()) }
        verify {
            ctx.completePendingWith(
                match {
                    error(
                        it,
                        message = "Runtime Error",
                        details = "detailed error message"
                    )
                }
            )
        }
    }

    @Test
    fun `dispatches error event when completePendingWith returns false`() {
        every { ctx.completePendingWith(any()) } returns false
        ctx.stubBody(10, "error body")

        handler.handle("401 Error 10", ctx)

        verify { ctx.dispatch(match { error(it, message = "Error", details = "error body") }) }
    }

    @Test
    fun `extracts error summary and length from header`() {
        val (message, length) = handler.parseErrorHeader("401 Runtime Error 100")
        assertThat(message).isEqualTo("Runtime Error")
        assertThat(length).isEqualTo(100)
    }

    @Test
    fun `extracts message when header contains single word`() {
        val (message, length) = handler.parseErrorHeader("401 Error 42")
        assertThat(message).isEqualTo("Error")
        assertThat(length).isEqualTo(42)
    }

    @Test
    fun `defaults error message when header omits summary`() {
        val (message, length) = handler.parseErrorHeader("401 50")
        assertThat(message).isEqualTo("Error")
        assertThat(length).isEqualTo(50)
    }

    @Test
    fun `supports zero-length error bodies`() {
        val (message, length) = handler.parseErrorHeader("401 Custom Error 0")
        assertThat(message).isEqualTo("Custom Error")
        assertThat(length).isEqualTo(0)
    }

    @Test
    fun `assumes zero length when header omits size`() {
        val (message, length) = handler.parseErrorHeader("401 Error Message")
        assertThat(message).isEqualTo("Error Message")
        assertThat(length).isEqualTo(0)
    }
}

class BadRequestResponseHandlerTest {
    private val handler = BadRequestResponseHandler()
    private val ctx = relaxedCtx()

    @Test
    fun `handles bad request with standard prefix`() {
        handler.handle("400 Bad Request Invalid Command", ctx)

        verify {
            ctx.completePendingWith(
                match {
                    error(
                        it,
                        message = "Bad Request",
                        details = "Bad Request Invalid Command"
                    )
                }
            )
        }
        verify { ctx.dispatch(any()) }
    }

    @Test
    fun `handles bad request without standard prefix`() {
        handler.handle("400 Unknown Command", ctx)

        verify {
            ctx.completePendingWith(
                match {
                    error(
                        it,
                        message = "Bad Request",
                        details = "Bad Request Unknown Command"
                    )
                }
            )
        }
    }

    @Test
    fun `handles minimal bad request`() {
        handler.handle("400", ctx)

        verify { ctx.completePendingWith(match { error(it, message = "Bad Request", details = "Bad Request") }) }
    }

    @Test
    fun `always dispatches event even when pending completes`() {
        every { ctx.completePendingWith(any()) } returns true

        handler.handle("400 Bad Request", ctx)

        verify { ctx.completePendingWith(any()) }
        verify { ctx.dispatch(any()) }
    }

    @Test
    fun `dispatches when no pending callback exists`() {
        every { ctx.completePendingWith(any()) } returns false

        handler.handle("400 Error", ctx)

        verify { ctx.completePendingWith(any()) }
        verify { ctx.dispatch(any()) }
    }
}

private fun relaxedCtx(): MobDebugProtocol.Ctx = mockk(relaxed = true)

private fun MobDebugProtocol.Ctx.stubBody(
    length: Int,
    payload: String
) {
    val bodyCallback = slot<(String) -> Unit>()
    every { awaitBody(length, capture(bodyCallback)) } answers {
        bodyCallback.captured(payload)
    }
}

private fun ok(
    event: Event,
    message: String? = null
) = event is Event.Ok && event.message == message

private fun paused(
    event: Event,
    file: String,
    line: Int,
    watchIndex: Int? = null
) = event is Event.Paused && event.file == file && event.line == line && event.watchIndex == watchIndex

private fun output(
    event: Event,
    stream: String,
    text: String
) = event is Event.Output && event.stream == stream && event.text == text

private fun error(
    event: Event,
    message: String,
    details: String?
) = event is Event.Error && event.message == message && event.details == details

private fun unknown(event: Event) = event is Event.Unknown
