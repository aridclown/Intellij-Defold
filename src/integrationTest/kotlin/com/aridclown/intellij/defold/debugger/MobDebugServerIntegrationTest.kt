package com.aridclown.intellij.defold.debugger

import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.junit5.TestApplication
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@TestApplication
class MobDebugServerIntegrationTest {

    private val logger = mockk<Logger>(relaxed = true)
    private lateinit var server: MobDebugServer
    private val testPort = 8172

    companion object {
        private val DEFAULT_TIMEOUT = 1L to TimeUnit.SECONDS
    }

    @BeforeEach
    fun setUp() {
        server = MobDebugServer("localhost", testPort, logger)
    }

    @AfterEach
    fun tearDown() {
        server.dispose()
        clearAllMocks()
    }

    @Test
    fun `startServer() is idempotent`() {
        server.startServer()

        // Starting again should not throw
        server.startServer()

        Socket("localhost", testPort).use {
            assertThat(it.isConnected).isTrue
        }
    }

    @Test
    fun `dispose() stops server and closes connections`() {
        val connectLatch = CountDownLatch(1)
        server.addOnConnectedListener { connectLatch.countDown() }

        server.startServer()

        Socket("localhost", testPort).use {
            assertThat(connectLatch.awaitDefault()).isTrue
            assertThat(server.isConnected()).isTrue

            server.dispose()

            // Server socket should be closed
            assertThrows<ConnectException> { Socket("localhost", testPort) }

            assertThat(server.isConnected()).isFalse
        }
    }

    @Nested
    inner class ClientConnection {

        @Test
        fun `accepts and notifies client connection`() {
            startServerAndConnect {
                assertThat(server.isConnected()).isTrue
            }
        }

        @Test
        fun `notifies on client disconnection`() {
            val disconnectLatch = CountDownLatch(1)
            var disconnected = false

            server.addOnDisconnectedListener {
                disconnected = true
                disconnectLatch.countDown()
            }

            startServerAndConnect { }

            assertThat(disconnectLatch.awaitDefault()).isTrue
            assertThat(disconnected).isTrue
        }

        @Test
        fun `rejects duplicate connections`() {
            val duplicateLatch = CountDownLatch(1)
            server.addOnDuplicateConnectionListener { duplicateLatch.countDown() }

            startServerAndConnect {
                assertThat(server.isConnected()).isTrue
            }

            // Second connection should be rejected
            Socket("localhost", testPort).use {
                assertThat(duplicateLatch.awaitDefault()).isTrue
            }
        }

        @Test
        fun `handles listener exceptions gracefully`() {
            val latch = CountDownLatch(1)

            server.addListener {
                throw RuntimeException("Test exception")
            }

            server.addListener {
                latch.countDown() // This should still be called
            }

            startServerAndConnect { socket ->
                socket.writer().apply {
                    write("200 OK\n")
                    flush()
                }

                assertThat(latch.awaitDefault()).isTrue

                verify(atLeast = 1) { logger.warn(any(), any<Throwable>()) }
            }
        }
    }

    @Nested
    inner class MessageHandling {

        @Test
        fun `sends commands to connected client`() {
            startServerAndConnect { socket ->
                server.send("RUN")
                val received = socket.reader().readLine()

                assertThat(received).isEqualTo("RUN")
            }
        }

        @Test
        fun `receives messages from client`() {
            val messageLatch = CountDownLatch(1)
            var receivedMessage: String? = null

            server.addListener { message ->
                receivedMessage = message
                messageLatch.countDown()
            }

            startServerAndConnect { socket ->
                socket.writer().apply {
                    write("200 OK\n")
                    flush()
                }

                assertThat(messageLatch.awaitDefault()).isTrue
                assertThat(receivedMessage).isEqualTo("200 OK")
            }
        }

        @Test
        fun `queues commands sent before client connects`() {
            server.startServer()

            // Send commands before client connects
            server.send("BASEDIR /project")
            server.send("RUN")

            val connectLatch = CountDownLatch(1)
            server.addOnConnectedListener { connectLatch.countDown() }

            Socket("localhost", testPort).use { socket ->
                assertThat(connectLatch.awaitDefault()).isTrue

                val reader = socket.reader()

                // Queued commands should be flushed on connection
                assertThat(reader.readLine()).isEqualTo("BASEDIR /project")
                assertThat(reader.readLine()).isEqualTo("RUN")
            }
        }
    }

    @Nested
    inner class BodyRequests {

        @Test
        fun `reads length-prefixed body after line`() {
            val bodyLatch = CountDownLatch(1)
            var receivedBody: String? = null

            startServerAndConnect { socket ->
                server.requestBody(11) { body ->
                    receivedBody = body
                    bodyLatch.countDown()
                }

                socket.writer().apply {
                    write("200 OK 11\n")
                    write("Hello World")
                    flush()
                }

                assertThat(bodyLatch.awaitDefault()).isTrue
                assertThat(receivedBody).isEqualTo("Hello World")
            }
        }

        @Test
        fun `handles zero-length body`() {
            val bodyLatch = CountDownLatch(1)
            var receivedBody: String? = null

            startServerAndConnect { socket ->
                server.requestBody(0) { body ->
                    receivedBody = body
                    bodyLatch.countDown()
                }

                socket.writer().apply {
                    write("200 OK 0\n")
                    flush()
                }

                assertThat(bodyLatch.awaitDefault()).isTrue
                assertThat(receivedBody).isEmpty()
            }
        }

        @Test
        fun `reads multi-byte characters in body correctly`() {
            val bodyLatch = CountDownLatch(1)
            var receivedBody: String? = null
            val unicodeText = "你好世界"

            startServerAndConnect { socket ->
                server.requestBody(unicodeText.length) { body ->
                    receivedBody = body
                    bodyLatch.countDown()
                }

                socket.writer().apply {
                    write("200 OK ${unicodeText.toByteArray().size}\n")
                    write(unicodeText)
                    flush()
                }

                assertThat(bodyLatch.awaitDefault()).isTrue
                assertThat(receivedBody).isEqualTo(unicodeText)
            }
        }
    }

    private fun startServerAndConnect(block: (Socket) -> Unit) {
        val connectLatch = CountDownLatch(1)
        server.addOnConnectedListener { connectLatch.countDown() }
        server.startServer()

        Socket("localhost", testPort).use { socket ->
            assertThat(connectLatch.await(DEFAULT_TIMEOUT.first, DEFAULT_TIMEOUT.second)).isTrue
            block(socket)
        }
    }

    private fun Socket.writer() = BufferedWriter(OutputStreamWriter(getOutputStream()))
    private fun Socket.reader() = BufferedReader(InputStreamReader(getInputStream()))

    private fun CountDownLatch.awaitDefault() = await(DEFAULT_TIMEOUT.first, DEFAULT_TIMEOUT.second)
}