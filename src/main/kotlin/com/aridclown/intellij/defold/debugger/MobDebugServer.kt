package com.aridclown.intellij.defold.debugger

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * MobDebug server that listens for connections from Defold games.
 */
class MobDebugServer(
    private val host: String,
    private val port: Int,
    private val logger: Logger
) : Disposable {

    private lateinit var serverSocket: ServerSocket
    private lateinit var clientSocket: Socket
    private lateinit var reader: BufferedReader
    private lateinit var writer: BufferedWriter
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val executor = Executors.newSingleThreadExecutor()
    private var isListening = false
    private val pendingCommands = CopyOnWriteArrayList<String>()

    fun startServer() {
        if (isListening) return

        try {
            // Use explicit bind with reuseAddress to avoid TIME_WAIT bind issues on restart
            serverSocket = ServerSocket()
            serverSocket.reuseAddress = true
            serverSocket.bind(InetSocketAddress(port))
            isListening = true
            println("MobDebug server started at $host:$port - waiting for Defold connection...")

            // Wait for client connection in the background
            executor.submit {
                try {
                    serverSocket.accept()
                        ?.let { handleClientConnection(it) }
                } catch (e: IOException) {
                    if (isListening) {
                        // Only log if we're still supposed to be listening
                        logger.warn("MobDebug server accept error", e)
                    }
                }
            }
        } catch (e: IOException) {
            logger.error("Failed to start MobDebug server", e)
            throw e
        }
    }

    private fun handleClientConnection(socket: Socket) {
        try {
            clientSocket = socket
            reader = BufferedReader(InputStreamReader(socket.getInputStream(), UTF_8))
            writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), UTF_8))

            println("MobDebug client connected from ${socket.remoteSocketAddress}")
            println("Defold game connected! Debugging session started.")

            // Clear all remote breakpoints before applying ours to avoid stale state
            send("DELB * 0")

            // Flush any commands queued before the connection was established (e.g., SETB)
            pendingCommands
                .onEach { send(it) }
                .clear()

            startReading()
        } catch (e: IOException) {
            logger.warn("Error setting up client connection", e)
        }
    }

    private fun startReading() {
        executor.submit {
            try {
                reader.forEachLine { line ->
                    logger.info("<-- $line")
                    listeners.forEach { it(line) }
                }
            } catch (e: IOException) {
                when {
                    e.message?.contains("Stream closed") == true -> println("Defold game disconnected. ${e.message}")
                    else -> logger.warn("MobDebug read error", e)
                }
            }
        }
    }

    fun send(command: String) {
        if (!::writer.isInitialized) {
            // Queue until a client connects
            pendingCommands.add(command)
            logger.info("(queued) --> $command")
            return
        }

        try {
            logger.info("--> $command")
            writer.apply {
                write(command)
                write("\n")
                flush()
            }
        } catch (e: IOException) {
            logger.warn("MobDebug write error on $command command", e)
        }
    }

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun isConnected(): Boolean = ::clientSocket.isInitialized && clientSocket.isConnected

    override fun dispose() {
        isListening = false
        try {
            if (::reader.isInitialized) try {
                reader.close()
            } catch (_: IOException) {
            }
            if (::writer.isInitialized) try {
                writer.close()
            } catch (_: IOException) {
            }
            if (::clientSocket.isInitialized) try {
                clientSocket.close()
            } catch (_: IOException) {
            }
            if (::serverSocket.isInitialized) try {
                serverSocket.close()
            } catch (_: IOException) {
            }
        } catch (e: Exception) {
            logger.warn("MobDebug server close error", e)
        } finally {
            executor.shutdownNow()
            pendingCommands.clear()
        }
    }
}
