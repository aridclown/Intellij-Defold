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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    private fun simulateResponse(line: String) {
        capturedListeners.forEach { it(line) }
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
            val latch = CountDownLatch(1)
            var receivedEvent: Event? = null

            protocol.run { event ->
                receivedEvent = event
                latch.countDown()
            }

            simulateResponse("200 OK")
            latch.await(1, TimeUnit.SECONDS)

            assertThat(receivedEvent).isInstanceOf(Event.Ok::class.java)
            assertThat((receivedEvent as Event.Ok).message).isNull()
        }

        @Test
        fun `parses 200 OK with message`() {
            val latch = CountDownLatch(1)
            var receivedEvent: Event? = null

            protocol.run { event ->
                receivedEvent = event
                latch.countDown()
            }

            simulateResponse("200 OK Resumed")
            latch.await(1, TimeUnit.SECONDS)

            assertThat(receivedEvent).isInstanceOf(Event.Ok::class.java)
            assertThat((receivedEvent as Event.Ok).message).isEqualTo("Resumed")
        }

        @Test
        fun `parses 202 Paused response`() {
            val latch = CountDownLatch(1)
            var receivedEvent: Event? = null

            protocol.addListener { event ->
                receivedEvent = event
                latch.countDown()
            }

            simulateResponse("202 Paused /main/game.lua 42")
            latch.await(1, TimeUnit.SECONDS)

            assertThat(receivedEvent).isInstanceOf(Event.Paused::class.java)
            val paused = receivedEvent as Event.Paused
            assertThat(paused.file).isEqualTo("/main/game.lua")
            assertThat(paused.line).isEqualTo(42)
            assertThat(paused.watchIndex).isNull()
        }

        @Test
        fun `parses 203 Paused with watch index`() {
            val latch = CountDownLatch(1)
            var receivedEvent: Event? = null

            protocol.addListener { event ->
                receivedEvent = event
                latch.countDown()
            }

            simulateResponse("203 Paused /main/game.lua 42 1")
            latch.await(1, TimeUnit.SECONDS)

            assertThat(receivedEvent).isInstanceOf(Event.Paused::class.java)
            val paused = receivedEvent as Event.Paused
            assertThat(paused.file).isEqualTo("/main/game.lua")
            assertThat(paused.line).isEqualTo(42)
            assertThat(paused.watchIndex).isEqualTo(1)
        }

        @Test
        fun `parses 400 Bad Request`() {
            val latch = CountDownLatch(1)
            var receivedEvent: Event? = null

            protocol.run { event ->
                receivedEvent = event
                latch.countDown()
            }

            simulateResponse("400 Bad Request")
            latch.await(1, TimeUnit.SECONDS)

            assertThat(receivedEvent).isInstanceOf(Event.Error::class.java)
            val error = receivedEvent as Event.Error
            assertThat(error.message).isEqualTo("Bad Request")
        }

        @Test
        fun `parses 401 Error with length-prefixed body`() {
            val latch = CountDownLatch(1)
            var receivedEvent: Event? = null

            protocol.exec("invalid", onResult = {}, onError = { event ->
                receivedEvent = event
                latch.countDown()
            })

            every { server.requestBody(12, any()) } answers {
                secondArg<(String) -> Unit>().invoke("syntax error")
            }

            simulateResponse("401 Error 12")
            latch.await(1, TimeUnit.SECONDS)

            assertThat(receivedEvent).isInstanceOf(Event.Error::class.java)
            val error = receivedEvent as Event.Error
            assertThat(error.message).isEqualTo("Error")
            assertThat(error.details).isEqualTo("syntax error")
        }

        @Test
        fun `parses unknown status code as Unknown event`() {
            val latch = CountDownLatch(1)
            var receivedEvent: Event? = null

            protocol.addListener { event ->
                receivedEvent = event
                latch.countDown()
            }

            simulateResponse("999 Unknown Status")
            latch.await(1, TimeUnit.SECONDS)

            assertThat(receivedEvent).isInstanceOf(Event.Unknown::class.java)
            assertThat((receivedEvent as Event.Unknown).line).isEqualTo("999 Unknown Status")
        }
    }

    @Nested
    inner class MultiLineResponses {

        @Test
        fun `handles EXEC response with body`() {
            val latch = CountDownLatch(1)
            var result: String? = null

            protocol.exec("return 42", onResult = { response ->
                result = response
                latch.countDown()
            })

            every { server.requestBody(2, any()) } answers {
                secondArg<(String) -> Unit>().invoke("42")
            }

            simulateResponse("200 OK 2")
            latch.await(1, TimeUnit.SECONDS)

            assertThat(result).isEqualTo("42")
        }

        @Test
        fun `handles STACK response with serpent data`() {
            val latch = CountDownLatch(1)
            var result: String? = null

            protocol.stack(onResult = { response ->
                result = response
                latch.countDown()
            })

            val stackData = "{level=1,func=\"main\"}"
            every { server.requestBody(stackData.length, any()) } answers {
                secondArg<(String) -> Unit>().invoke(stackData)
            }

            simulateResponse("200 OK ${stackData.length}")
            latch.await(1, TimeUnit.SECONDS)

            assertThat(result).isEqualTo(stackData)
        }

        @Test
        fun `handles OUTPUT response with body`() {
            val latch = CountDownLatch(1)
            var receivedEvent: Event? = null

            protocol.addListener { event ->
                receivedEvent = event
                latch.countDown()
            }

            every { server.requestBody(11, any()) } answers {
                secondArg<(String) -> Unit>().invoke("Hello World")
            }

            simulateResponse("204 Output stdout 11")
            latch.await(1, TimeUnit.SECONDS)

            assertThat(receivedEvent).isInstanceOf(Event.Output::class.java)
            val output = receivedEvent as Event.Output
            assertThat(output.stream).isEqualTo("stdout")
            assertThat(output.text).isEqualTo("Hello World")
        }
    }

    @Nested
    inner class CallbackHandling {

        @Test
        fun `invokes onResult callback on successful response`() {
            val latch = CountDownLatch(1)
            var callbackInvoked = false

            protocol.run { 
                callbackInvoked = true
                latch.countDown()
            }

            simulateResponse("200 OK")
            latch.await(1, TimeUnit.SECONDS)

            assertThat(callbackInvoked).isTrue()
        }

        @Test
        fun `invokes onError callback on error response`() {
            val latch = CountDownLatch(1)
            var errorReceived: Event.Error? = null

            every { server.requestBody(5, any()) } answers {
                secondArg<(String) -> Unit>().invoke("error")
            }

            protocol.exec("bad", onResult = {}, onError = { error ->
                errorReceived = error
                latch.countDown()
            })

            simulateResponse("401 Error 5")
            latch.await(1, TimeUnit.SECONDS)

            assertThat(errorReceived).isNotNull()
            assertThat(errorReceived?.message).isEqualTo("Error")
        }

        @Test
        fun `notifies all registered listeners`() {
            val latch1 = CountDownLatch(1)
            val latch2 = CountDownLatch(1)
            var listener1Called = false
            var listener2Called = false

            protocol.addListener {
                listener1Called = true
                latch1.countDown()
            }

            protocol.addListener {
                listener2Called = true
                latch2.countDown()
            }

            simulateResponse("202 Paused /main/game.lua 1")
            latch1.await(1, TimeUnit.SECONDS)
            latch2.await(1, TimeUnit.SECONDS)

            assertThat(listener1Called).isTrue()
            assertThat(listener2Called).isTrue()
        }
    }

    @Nested
    inner class UnicodeHandling {

        @Test
        fun `handles unicode in variable values`() {
            val latch = CountDownLatch(1)
            var result: String? = null

            protocol.exec("return \"你好\"", onResult = { response ->
                result = response
                latch.countDown()
            })

            val unicodeValue = "你好世界"
            every { server.requestBody(unicodeValue.length, any()) } answers {
                secondArg<(String) -> Unit>().invoke(unicodeValue)
            }

            simulateResponse("200 OK ${unicodeValue.length}")
            latch.await(1, TimeUnit.SECONDS)

            assertThat(result).isEqualTo(unicodeValue)
        }

        @Test
        fun `handles unicode in file paths`() {
            val latch = CountDownLatch(1)
            var receivedEvent: Event? = null

            protocol.addListener { event ->
                receivedEvent = event
                latch.countDown()
            }

            simulateResponse("202 Paused /main/游戏.lua 10")
            latch.await(1, TimeUnit.SECONDS)

            assertThat(receivedEvent).isInstanceOf(Event.Paused::class.java)
            val paused = receivedEvent as Event.Paused
            assertThat(paused.file).isEqualTo("/main/游戏.lua")
        }
    }
}
