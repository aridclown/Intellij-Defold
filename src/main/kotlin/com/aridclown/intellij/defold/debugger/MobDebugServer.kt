package com.aridclown.intellij.defold.debugger

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
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

    fun startServer() {
        if (isListening) return

        try {
            serverSocket = ServerSocket(port)
            isListening = true
            logger.info("MobDebug server started at $host:$port")
            println("MobDebug server started at $host:$port - waiting for Defold connection...")

            // Wait for client connection in the background
            executor.submit {
                try {
                    val socket = serverSocket.accept()
                    if (socket != null) {
                        handleClientConnection(socket)
                    }
                } catch (e: IOException) {
                    if (isListening) { // Only log if we're still supposed to be listening
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
            reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))

            println("MobDebug client connected from ${socket.remoteSocketAddress}")
            println("Defold game connected! Debugging session started.")

            startReading()
        } catch (e: IOException) {
            logger.warn("Error setting up client connection", e)
        }
    }

    private fun startReading() {
        // First, we need to send the "RUN" command to the game, otherwise it won't start debugging.'
        send("RUN")

        executor.submit {
            try {
                reader.forEachLine { line ->
                    logger.info("<-- $line")
                    listeners.forEach { it(line) }
                }
            } catch (e: IOException) {
                logger.warn("MobDebug read error", e)
                println("Defold game disconnected. ${e.message}")
            }
        }
    }

    fun send(command: String) {
        if (!::writer.isInitialized) {
            logger.warn("MobDebug writer is not initialized. Cannot send command: $command")
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
            logger.warn("MobDebug write error", e)
        }
    }

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun isConnected(): Boolean = clientSocket.isConnected

    override fun dispose() {
        if (!::serverSocket.isInitialized || !::clientSocket.isInitialized) return

        isListening = false
        try {
            clientSocket.close()
            serverSocket.close()
        } catch (e: IOException) {
            logger.warn("MobDebug server close error", e)
        }
        executor.shutdownNow()
    }
}
