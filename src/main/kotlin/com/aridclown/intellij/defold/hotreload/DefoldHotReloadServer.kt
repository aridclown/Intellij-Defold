package com.aridclown.intellij.defold.hotreload

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.Executors

/**
 * HTTP server that serves build artifacts with ETags, mirroring the Defold editor's hot reload server.
 * 
 * Implements the same routes as the editor:
 * - GET /build/{path} - Serves build artifacts with ETag headers and 304 caching
 * - POST /__verify_etags__ - Validates client ETags for bulk cache verification
 */
class DefoldHotReloadServer(
    private val buildDir: File,
    private val port: Int,
    private val compiledArtifacts: Map<String, BuildArtifact>,
    private val normalizedArtifacts: Map<String, BuildArtifact>
) {
    private var server: HttpServer? = null
    private val executor = Executors.newFixedThreadPool(4)

    fun start() {
        try {
            server = HttpServer.create(InetSocketAddress(port), 0).apply {
                createContext("/build/", BuildHandler())
                createContext("/__verify_etags__", VerifyEtagsHandler())
                this.executor = this@DefoldHotReloadServer.executor
                start()
            }
            println("Hot reload server started on port $port")
        } catch (e: Exception) {
            println("Failed to start hot reload server: ${e.message}")
        }
    }

    fun stop() {
        server?.stop(0)
        executor.shutdown()
        server = null
    }

    private inner class BuildHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val requestPath = exchange.requestURI.path.removePrefix("/build")
            val artifact = findArtifact(requestPath)
            val targetFile = artifact?.file ?: File(buildDir, requestPath.removePrefix("/"))

            println("[HotReloadServer] GET ${exchange.requestURI.path}")
            println("[HotReloadServer] -> requested path: $requestPath")
            println("[HotReloadServer] -> artifact: ${artifact?.compiledPath}")
            println("[HotReloadServer] -> target file: $targetFile (exists: ${targetFile.exists()})")

            try {
                // Security check: prevent directory traversal
                if (!targetFile.canonicalPath.startsWith(buildDir.canonicalPath)) {
                    println("[HotReloadServer] Security violation: path outside build dir")
                    sendResponse(exchange, 403, "Forbidden")
                    return
                }

                if (!targetFile.exists() || targetFile.isDirectory) {
                    println("[HotReloadServer] File not found or is directory")
                    sendResponse(exchange, 404, "Not Found")
                    return
                }

                val etag = artifact?.etag ?: calculateFileEtag(targetFile)
                val clientEtag = exchange.requestHeaders.getFirst("If-None-Match")

                println("[HotReloadServer] -> ETag: server=$etag, client=$clientEtag")

                // Return 304 if client has current version
                if (clientEtag != null && clientEtag == etag) {
                    exchange.responseHeaders.set("ETag", etag)
                    exchange.sendResponseHeaders(304, -1)
                    println("[HotReloadServer] -> Returned 304 Not Modified")
                    return
                }

                // Send file with ETag
                val fileBytes = Files.readAllBytes(targetFile.toPath())
                exchange.responseHeaders.set("ETag", etag)
                exchange.responseHeaders.set("Content-Type", "application/octet-stream")
                exchange.sendResponseHeaders(200, fileBytes.size.toLong())
                exchange.responseBody.use { it.write(fileBytes) }
                println("[HotReloadServer] -> Sent file (${fileBytes.size} bytes)")

            } catch (e: Exception) {
                println("[HotReloadServer] Error serving file: ${e.message}")
                sendResponse(exchange, 500, "Internal Server Error: ${e.message}")
            }
        }

        private fun calculateFileEtag(file: File): String {
            val bytes = file.readBytes()
            val digest = java.security.MessageDigest.getInstance("MD5")
            val hash = digest.digest(bytes)
            return hash.joinToString("") { "%02x".format(it) }
        }
    }

    private inner class VerifyEtagsHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            if (exchange.requestMethod != "POST") {
                sendResponse(exchange, 405, "Method Not Allowed")
                return
            }

            try {
                val requestBody = exchange.requestBody.bufferedReader().use { it.readText() }
                val validPaths = mutableListOf<String>()

                requestBody.lines().forEach { line ->
                    val parts = line.split(" ", limit = 2)
                    if (parts.size == 2) {
                        val url = parts[0]
                        val clientEtag = parts[1]
                        
                        if (url.startsWith("/build/")) {
                            val path = url.removePrefix("/build")
                            val serverEtag = findArtifact(path)?.etag
                            
                            // Keep only valid ETags
                            if (serverEtag != null && serverEtag == clientEtag) {
                                validPaths.add(url)
                            }
                        }
                    }
                }

                if (validPaths.isEmpty()) {
                    println("[HotReloadServer] VERIFY etags miss for paths:\n$requestBody")
                }

                val response = validPaths.joinToString("\n")
                exchange.responseHeaders.set("Content-Type", "text/plain")
                exchange.sendResponseHeaders(200, response.length.toLong())
                exchange.responseBody.use { it.write(response.toByteArray()) }

            } catch (e: Exception) {
                sendResponse(exchange, 500, "Internal Server Error: ${e.message}")
            }
        }
    }

    private fun sendResponse(exchange: HttpExchange, statusCode: Int, message: String) {
        try {
            exchange.responseHeaders.set("Content-Type", "text/plain")
            exchange.sendResponseHeaders(statusCode, message.length.toLong())
            exchange.responseBody.use { it.write(message.toByteArray()) }
        } catch (e: Exception) {
            // Ignore errors when sending error responses
        }
    }

    private fun findArtifact(requestPath: String): BuildArtifact? {
        return compiledArtifacts[requestPath]
            ?: normalizedArtifacts[requestPath]
    }
}
