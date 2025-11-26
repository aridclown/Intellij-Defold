package com.aridclown.intellij.defold.debugger

import com.intellij.openapi.diagnostic.Logger
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MobDebugProtocolTest {
    private val server = mockk<MobDebugServer>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private lateinit var protocol: MobDebugProtocol
    private val capturedListeners = mutableListOf<(String) -> Unit>()

    @BeforeEach
    fun setUp() {
        every { server.addListener(any()) } answers {
            capturedListeners.add(firstArg())
        }
        protocol = MobDebugProtocol(server, logger)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        capturedListeners.clear()
    }

    @Nested
    inner class CommandSerialization {
        @Test
        fun `run() sends RUN command`() {
            protocol.run()

            verify(exactly = 1) { server.send("RUN") }
        }

        @Test
        fun `step() sends STEP command`() {
            protocol.step()

            verify(exactly = 1) { server.send("STEP") }
        }

        @Test
        fun `over() sends OVER command`() {
            protocol.over()

            verify(exactly = 1) { server.send("OVER") }
        }

        @Test
        fun `out() sends OUT command`() {
            protocol.out()

            verify(exactly = 1) { server.send("OUT") }
        }

        @Test
        fun `suspend() sends SUSPEND command`() {
            protocol.suspend()

            verify(exactly = 1) { server.send("SUSPEND") }
        }

        @Test
        fun `exit() sends EXIT command`() {
            protocol.exit()

            verify(exactly = 1) { server.send("EXIT") }
        }

        @Test
        fun `setBreakpoint() sends SETB with file and line`() {
            protocol.setBreakpoint("/main/game.lua", 42)

            verify(exactly = 1) { server.send("SETB /main/game.lua 42") }
        }

        @Test
        fun `deleteBreakpoint() sends DELB with file and line`() {
            protocol.deleteBreakpoint("/main/game.lua", 42)

            verify(exactly = 1) { server.send("DELB /main/game.lua 42") }
        }

        @Test
        fun `clearAllBreakpoints() sends DELB wildcard`() {
            protocol.clearAllBreakpoints()

            verify(exactly = 1) { server.send("DELB * 0") }
        }

        @Test
        fun `basedir() sends BASEDIR command`() {
            protocol.basedir("/project")

            verify(exactly = 1) { server.send("BASEDIR /project") }
        }

        @Test
        fun `outputStdout() sends OUTPUT command`() {
            protocol.outputStdout('c')

            verify(exactly = 1) { server.send("OUTPUT stdout c") }
        }

        @Test
        fun `stack() sends STACK command`() {
            protocol.stack(onResult = {})

            verify(exactly = 1) { server.send("STACK") }
        }

        @Test
        fun `stack() with options sends STACK with parameters`() {
            protocol.stack("nocode=1", onResult = {})

            verify(exactly = 1) { server.send("STACK -- nocode=1") }
        }

        @Test
        fun `exec() sends EXEC command`() {
            protocol.exec("return 42", onResult = {})

            verify(exactly = 1) { server.send("EXEC return 42") }
        }

        @Test
        fun `exec() with frame sends EXEC with stack parameter`() {
            protocol.exec("return self", frame = 2, onResult = {})

            verify(exactly = 1) { server.send("EXEC return self -- { stack = 2 }") }
        }

        @Test
        fun `exec() with options sends EXEC with parameters`() {
            protocol.exec("return value", options = "maxlevel=3", onResult = {})

            verify(exactly = 1) { server.send("EXEC return value -- { , maxlevel=3 }") }
        }

        @Test
        fun `exec() with frame and options combines parameters`() {
            protocol.exec("print(x)", frame = 1, options = "maxlevel=2", onResult = {})

            verify(exactly = 1) { server.send("EXEC print(x) -- { stack = 1, maxlevel=2 }") }
        }
    }

    @Nested
    inner class ResponseParsing {
        @Test
        fun `parses 200 OK simple response`() {
            protocol.run { event ->
                simulateResponse("200 OK")

                assertThat(event).isInstanceOf(Event.Ok::class.java)
                assertThat((event as Event.Ok).message).isNull()
            }
        }

        @Test
        fun `parses 200 OK with message`() {
            protocol.run { event ->
                simulateResponse("200 OK Resumed")

                assertThat(event).isInstanceOf(Event.Ok::class.java)
                assertThat((event as Event.Ok).message).isEqualTo("Resumed")
            }
        }

        @Test
        fun `parses 202 Paused response`() {
            protocol.addListener { event ->
                simulateResponse("202 Paused /main/game.lua 42")

                assertThat(event)
                    .isInstanceOf(Event.Paused::class.java)
                    .extracting { it as Event.Paused }
                    .extracting({ it.file }, { it.line }, { it.watchIndex })
                    .containsExactly("/main/game.lua", 42, null)
            }
        }

        @Test
        fun `parses 203 Paused with watch index`() {
            protocol.addListener { event ->
                simulateResponse("203 Paused /main/game.lua 42 1")

                assertThat(event)
                    .isInstanceOf(Event.Paused::class.java)
                    .extracting { it as Event.Paused }
                    .extracting({ it.file }, { it.line }, { it.watchIndex })
                    .containsExactly("/main/game.lua", 42, 1)
            }
        }

        @Test
        fun `parses 400 Bad Request`() {
            protocol.run { event ->
                simulateResponse("400 Bad Request")

                assertThat(event)
                    .isInstanceOf(Event.Error::class.java)
                    .extracting { (it as Event.Error).message }
                    .isEqualTo("Bad Request")
            }
        }

        @Test
        fun `parses 401 Error with length-prefixed body`() {
            every { server.requestBody(12, any()) } answers {
                secondArg<(String) -> Unit>().invoke("syntax error")
            }

            protocol.exec("invalid", onResult = {}, onError = { event ->
                simulateResponse("401 Error 12")

                assertThat(event)
                    .isInstanceOf(Event.Error::class.java)
                    .extracting { it as Event.Error }
                    .extracting({ it.message }, { it.details })
                    .containsExactly("Error", "syntax error")
            })
        }

        @Test
        fun `parses unknown status code as Unknown event`() {
            protocol.addListener { event ->
                simulateResponse("999 Unknown Status")

                assertThat(event)
                    .isInstanceOf(Event.Unknown::class.java)
                    .extracting { (it as Event.Unknown).line }
                    .isEqualTo("999 Unknown Status")
            }
        }
    }

    @Nested
    inner class MultiLineResponses {
        @Test
        fun `handles EXEC response with body`() {
            every { server.requestBody(2, any()) } answers {
                secondArg<(String) -> Unit>().invoke("42")
            }

            protocol.exec("return 42", onResult = { response ->
                simulateResponse("200 OK 2")

                assertThat(response).isEqualTo("42")
            })
        }

        @Test
        fun `handles STACK response with serpent data`() {
            val stackData = "{level=1,func=\"main\"}"
            every { server.requestBody(stackData.length, any()) } answers {
                secondArg<(String) -> Unit>().invoke(stackData)
            }

            protocol.stack(onResult = { response ->
                simulateResponse("200 OK ${stackData.length}")

                assertThat(response).isEqualTo(stackData)
            })
        }

        @Test
        fun `handles OUTPUT response with body`() {
            every { server.requestBody(11, any()) } answers {
                secondArg<(String) -> Unit>().invoke("Hello World")
            }

            protocol.addListener { event ->
                simulateResponse("204 Output stdout 11")

                assertThat(event)
                    .isInstanceOf(Event.Output::class.java)
                    .extracting { it as Event.Output }
                    .extracting({ it.stream }, { it.text })
                    .containsExactly("stdout", "Hello World")
            }
        }
    }

    @Nested
    inner class CallbackHandling {
        @Test
        fun `invokes onResult callback on successful response`() {
            var callbackInvoked = false

            protocol.run {
                callbackInvoked = true
            }

            simulateResponse("200 OK")
            assertThat(callbackInvoked).isTrue
        }

        @Test
        fun `invokes onError callback on error response`() {
            every { server.requestBody(5, any()) } answers {
                secondArg<(String) -> Unit>().invoke("error")
            }

            protocol.exec("bad", onResult = {}, onError = { error ->
                simulateResponse("401 Error 5")

                assertThat(error.message).isEqualTo("Error")
            })
        }

        @Test
        fun `notifies all registered listeners`() {
            var listener1Called = false
            var listener2Called = false

            protocol.addListener {
                listener1Called = true
            }

            protocol.addListener {
                listener2Called = true
            }

            simulateResponse("202 Paused /main/game.lua 1")

            assertThat(listener1Called).isTrue
            assertThat(listener2Called).isTrue
        }
    }

    @Nested
    inner class UnicodeHandling {
        @Test
        fun `handles unicode in variable values`() {
            val unicodeValue = "你好世界"
            every { server.requestBody(unicodeValue.length, any()) } answers {
                secondArg<(String) -> Unit>().invoke(unicodeValue)
            }

            protocol.exec("return \"你好\"", onResult = { response ->
                simulateResponse("200 OK ${unicodeValue.length}")

                assertThat(response).isEqualTo(unicodeValue)
            })
        }

        @Test
        fun `handles unicode in file paths`() {
            protocol.addListener { event ->
                simulateResponse("202 Paused /main/游戏.lua 10")

                assertThat(event)
                    .isInstanceOf(Event.Paused::class.java)
                    .extracting { (it as Event.Paused).file }
                    .isEqualTo("/main/游戏.lua")
            }
        }
    }

    private fun simulateResponse(line: String) = capturedListeners.forEach { it(line) }
}
