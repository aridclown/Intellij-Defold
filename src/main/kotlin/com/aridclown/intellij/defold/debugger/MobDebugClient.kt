package com.aridclown.intellij.defold.debugger

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Minimal socket client for the MobDebug protocol.
 * It exposes raw line based I/O and logs everything for inspection.
 */
class MobDebugClient(
    private val host: String,
    private val port: Int,
    private val logger: Logger
) : Disposable {

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val executor = Executors.newSingleThreadExecutor()

    fun connect() {
        if (socket != null) return
        socket = Socket(host, port)
        reader = BufferedReader(InputStreamReader(socket!!.getInputStream(), StandardCharsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream(), StandardCharsets.UTF_8))
        startReading()
        logger.info("MobDebug connected to $host:$port")
    }

    private fun startReading() {
        executor.submit {
            try {
                var line: String?
                while (reader.also { line = it?.readLine() } != null) {
                    val l = line
                    if (l != null) {
                        logger.debug("<-- $l")
                        listeners.forEach { it(l) }
                    }
                }
            } catch (e: IOException) {
                logger.warn("MobDebug read error", e)
            }
        }
    }

    fun send(command: String) {
        try {
            logger.debug("--> $command")
            writer?.apply {
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

    override fun dispose() {
        try {
            socket?.close()
        } catch (e: IOException) {
            logger.warn("MobDebug close error", e)
        }
        executor.shutdownNow()
    }
}

