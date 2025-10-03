package com.aridclown.intellij.defold.debugger

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
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

    @Volatile
    private var isListening = false

    @Volatile
    private var pendingBody: BodyRequest? = null
    private val pendingCommands = CopyOnWriteArrayList<String>()

    private data class BodyRequest(val len: Int, val onComplete: (String) -> Unit)

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

            // Wait for client connections in the background
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
                while (true) {
                    val line = reader.readLine() ?: break
                    println("<-- $line")
                    notifyMessageListeners(line)
                    pendingBody?.let { req ->
                        val buf = CharArray(req.len)
                        var read = 0
                        while (read < req.len) {
                            val n = reader.read(buf, read, req.len - read)
                            if (n <= 0) break
                            read += n
                        }
                        pendingBody = null
                        req.onComplete(String(buf, 0, read))
                    }
                }
            } catch (e: IOException) {
                handleStreamClosedException(e, "MobDebug read error")
            } finally {
                onDisconnected()
            }
        }
    }

    fun requestBody(len: Int, onComplete: (String) -> Unit) {
        pendingBody = BodyRequest(len, onComplete)
    }

    fun send(command: String) {
        if (!isConnected() || !::writer.isInitialized) {
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
            handleStreamClosedException(e, "MobDebug write error on $command command")
        }
    }

    fun isConnected(): Boolean = ::clientSocket.isInitialized && clientSocket.isConnected

    fun getPendingCommands(): List<String> = pendingCommands.toList()

    override fun dispose() {
        fun AutoCloseable.closeQuietly(): Result<Unit> = runCatching(AutoCloseable::close)
            .onFailure { logger.warn("MobDebug resource close error", it) }

        if (::reader.isInitialized) reader.closeQuietly()
        if (::writer.isInitialized) writer.closeQuietly()
        if (::clientSocket.isInitialized) clientSocket.closeQuietly()
        if (::serverSocket.isInitialized) serverSocket.closeQuietly()

        isListening = false
        pendingCommands.clear()
    }

    private fun handleStreamClosedException(e: IOException, warningMessage: String) = when {
        e.message?.contains("Stream closed") == true -> println("Defold game disconnected. ${e.message}")
        else -> logger.warn(warningMessage, e)
    }
}
