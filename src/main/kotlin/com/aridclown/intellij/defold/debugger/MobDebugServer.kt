package com.aridclown.intellij.defold.debugger

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.diagnostic.Logger
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.CopyOnWriteArrayList

/**
 * MobDebug server that listens for connections from Defold games.
 */
class MobDebugServer(
    private val host: String,
    private val port: Int,
    logger: Logger
) : ConnectionLifecycleHandler(logger), Disposable {

    private lateinit var serverSocket: ServerSocket
    private lateinit var clientSocket: Socket
    private lateinit var reader: BufferedReader
    private lateinit var writer: BufferedWriter
    private var isListening = false
    private val pendingCommands = CopyOnWriteArrayList<String>()

    fun startServer() {
        if (isListening) return

        try {
            // Use explicit bind with reuseAddress to avoid TIME_WAIT bind issues on restart
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                isListening = true
                bind(InetSocketAddress(port))
            }
            println("MobDebug server started at $host:$port - waiting for Defold connection...")

            // Wait for client connections in the background (accept loop)
            getApplication().executeOnPooledThread {
                loop@ while (isListening) {
                    runCatching { handleClientConnection(serverSocket.accept() ?: continue@loop) }
                        .takeIf { isListening }
                        ?.onFailure { logger.warn("MobDebug server accept error", it) }
                }
            }
        } catch (e: IOException) {
            logger.error("Failed to start MobDebug server", e)
            throw e
        }
    }

    private fun handleClientConnection(socket: Socket) = try {
        // Close any previous client first
        closeClientQuietly()

        clientSocket = socket
        reader = BufferedReader(InputStreamReader(socket.getInputStream(), UTF_8))
        writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), UTF_8))

        // Flush any commands queued before the connection was established (e.g., SETB)
        pendingCommands
            .onEach { send(it) }
            .clear()

        // Notify listeners and begin IO
        onConnected()

        // Start reading from the client
        startReading()

        println("MobDebug client connected from ${socket.remoteSocketAddress}")
    } catch (e: IOException) {
        logger.warn("Error setting up client connection", e)
    }

    private fun startReading() {
        isListening = true
        getApplication().executeOnPooledThread {
            try {
                reader.forEachLine { line ->
                    println("<-- $line")
                    notifyMessageListeners(line)
                }
            } catch (e: IOException) {
                when {
                    e.message?.contains("Stream closed") == true -> println("Defold game disconnected. ${e.message}")
                    else -> logger.warn("MobDebug read error", e)
                }
            } finally {
                closeClientQuietly()
                onDisconnected()
            }
        }
    }

    fun send(command: String) {
        if (!::writer.isInitialized) {
            // Queue until a client connects
            pendingCommands.add(command)
            println("(queued) --> $command")
            return
        }

        try {
            println("--> $command")
            writer.apply {
                write(command)
                write("\n")
                flush()
            }
        } catch (e: IOException) {
            logger.warn("MobDebug write error on $command command", e)
        }
    }

    fun isConnected(): Boolean = ::clientSocket.isInitialized && clientSocket.isConnected

    fun restart() {
        // Drop the current client but keep the server listening
        closeClientQuietly()
    }

    override fun dispose() {
        listOf(reader, writer, clientSocket, serverSocket).forEach {
            runCatching { it.close() }.onFailure { error ->
                logger.warn("MobDebug server close error", error)
            }
        }
        isListening = false
        pendingCommands.clear()
    }

    private fun closeClientQuietly() {
        runCatching { reader.close() }
        runCatching { writer.close() }
        runCatching { clientSocket.close() }
        println("MobDebug client disconnected.")
    }
}
