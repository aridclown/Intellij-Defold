package com.aridclown.intellij.defold.hotreload

import com.aridclown.intellij.defold.DefoldProjectBuilder
import com.aridclown.intellij.defold.DefoldProjectService
import com.aridclown.intellij.defold.engine.DefoldEngineDiscoveryService
import com.aridclown.intellij.defold.engine.DefoldEngineEndpoint
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.aridclown.intellij.defold.ui.NotificationService.notifyError
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.*
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Service for performing hot reload of Defold resources by implementing the editor's
 * HTTP server + ETag system for resource serving and reload coordination.
 *
 * This mimics the editor's hot reload flow:
 * 1. Start HTTP server to serve build artifacts with ETags
 * 2. Build project and track changed resources by comparing ETags
 * 3. Send reload command to engine with changed resource paths
 * 4. Engine fetches updated resources from our HTTP server
 */
@Service(PROJECT)
class DefoldHotReloadService(private val project: Project) {

    companion object {
        private const val HOT_RELOAD_FEATURE = "Hot Reload"
        private const val RELOAD_ENDPOINT = "/post/@resource/reload"
        private val HOT_RELOAD_EXTENSIONS = setOf("script", "lua", "gui_script", "go")
        private val KNOWN_BUILD_CONFIG_SEGMENTS = setOf("default", "debug", "release", "profile")
        private const val BUILD_TIMEOUT_SECONDS = 30L

        fun Project.hotReloadProjectService(): DefoldHotReloadService = service<DefoldHotReloadService>()
    }

    private val httpClient = HttpClients.createDefault()
    private val artifactsByNormalizedPath = mutableMapOf<String, BuildArtifact>()

    fun performHotReload() {
        val console = findActiveConsole() ?: run {
            project.notifyError(HOT_RELOAD_FEATURE, "Hot reload requires an active run or debug session")
            return
        }

        return try {
            // Ensure artifacts are cached
            artifactsByNormalizedPath.ifEmpty(::refreshBuildArtifacts)

            // Require a running engine before proceeding
            val endpoint = resolveEngineEndpoint()
            if (endpoint == null || !isEngineReachable(endpoint, console)) {
                console.appendToConsole("Defold engine not reachable. Make sure the game is running from IntelliJ")
                return
            }

            // Capture current artifacts before rebuild
            val oldArtifacts = artifactsByNormalizedPath.toMap()

            // Build the project to get updated resources
            val buildSuccess = buildProject(console)
            if (!buildSuccess) {
                console.appendToConsole("Build failed, cannot perform hot reload")
                return
            }
            console.appendToConsole("Defold build completed")

            // Update artifacts for all build outputs
            refreshBuildArtifacts()

            // Find resources that actually changed by comparing ETags
            val changedArtifacts = findChangedArtifacts(oldArtifacts).ifEmpty {
                return // No resource changes detected after build
            }

            val resourcePaths = changedArtifacts.map(BuildArtifact::normalizedPath)
            // Create a standard protobuf payload as expected by Defold engine
            val payload = createProtobufReloadPayload(resourcePaths)
            // Send the reload command to the engine with changed resource paths
            sendResourceReloadToEngine(endpoint, payload)

            console.appendToConsole("Reloaded ${resourcePaths.size} resources")
        } catch (e: Exception) {
            val message = e.message ?: e::class.java.simpleName
            console.appendToConsole("Hot reload failed: $message", ERROR_OUTPUT)
        }
    }

    private fun buildProject(console: ConsoleView): Boolean {
        val defoldService = project.getService(DefoldProjectService::class.java)
        val config = defoldService.editorConfig ?: run {
            console.appendToConsole("Error: Defold configuration not found", ERROR_OUTPUT)
            return false
        }

        val processExecutor = ProcessExecutor(console)
        val builder = DefoldProjectBuilder(console, processExecutor)

        // Build project synchronously
        var buildSuccess = false
        val latch = CountDownLatch(1)

        builder.buildProject(
            project = project,
            config = config,
            onBuildSuccess = {
                buildSuccess = true
                latch.countDown()
            },
            onBuildFailure = { _ ->
                buildSuccess = false
                latch.countDown()
            }
        ).onFailure {
            latch.countDown()
            return false
        }

        latch.await(BUILD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return buildSuccess
    }

    private fun refreshBuildArtifacts() {
        val buildDir = File(project.basePath, "build")
        if (!buildDir.exists()) {
            artifactsByNormalizedPath.clear()
            return
        }

        artifactsByNormalizedPath.clear()

        buildDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val compiledPath = "/" + file.relativeTo(buildDir).path.replace(File.separatorChar, '/')
                val normalizedPath = normalizeCompiledPath(compiledPath)
                val etag = calculateEtag(file)
                val artifact = BuildArtifact(normalizedPath, compiledPath, file, etag)

                artifactsByNormalizedPath[normalizedPath] = artifact
            }
    }

    private fun calculateEtag(file: File): String {
        val bytes = file.readBytes()
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun normalizeCompiledPath(compiledPath: String): String {
        // Remove leading slash and normalize path separators
        val trimmed = compiledPath.trimStart('/')

        // For Defold, compiled paths in build directory are structured as:
        // platform/architecture/path/to/resource.extension
        // We need to extract just the project-relative path
        val parts = trimmed.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return "/"
        }

        var index = 0

        if (parts[index] in KNOWN_BUILD_CONFIG_SEGMENTS) {
            index++
        }

        val normalizedParts = parts.drop(index)
        if (normalizedParts.isEmpty()) {
            return "/${parts.last()}"
        }

        return "/" + normalizedParts.joinToString("/")
    }

    private fun isHotReloadable(path: String): Boolean {
        val extension = path.substringAfterLast('.').removeSuffix("c")
        return extension in HOT_RELOAD_EXTENSIONS
    }

    private fun findChangedArtifacts(oldArtifacts: Map<String, BuildArtifact>): List<BuildArtifact> =
        artifactsByNormalizedPath.values.filter { artifact ->
            val old = oldArtifacts[artifact.normalizedPath] ?: return@filter false
            old.etag != artifact.etag && isHotReloadable(artifact.normalizedPath)
        }

    private fun resolveEngineEndpoint(): DefoldEngineEndpoint? =
        project.getService(DefoldEngineDiscoveryService::class.java)?.currentEndpoint()

    private fun isEngineReachable(endpoint: DefoldEngineEndpoint, console: ConsoleView?): Boolean = try {
        // Try to access the engine info endpoint to check its capabilities
        val pingUrl = "http://${endpoint.address}:${endpoint.port}/ping"
        val request = HttpGet(pingUrl).apply {
            config = RequestConfig.custom()
                .setConnectTimeout(2000)
                .setSocketTimeout(3000)
                .build()
        }

        executeRequest(request) { response -> response.statusLine.statusCode in 200..299 }
    } catch (e: Exception) {
        console.appendToConsole("Engine info check failed: ${e.message}", ERROR_OUTPUT)
        false
    }

    private fun sendResourceReloadToEngine(
        endpoint: DefoldEngineEndpoint,
        payload: ByteArray
    ) {
        val url = "http://${endpoint.address}:${endpoint.port}$RELOAD_ENDPOINT"

        try {
            val request = HttpPost(url).apply {
                setHeader("Content-Type", "application/x-protobuf")
                entity = ByteArrayEntity(payload)
                config = RequestConfig.custom()
                    .setConnectTimeout(2000) // 2 seconds
                    .setSocketTimeout(5000)  // 5 seconds
                    .setExpectContinueEnabled(false)
                    .build()
            }

            executeRequest(request) { response ->
                val statusCode = response.statusLine.statusCode
                if (statusCode !in 200..299) {
                    throw IOException("Engine reload request failed with status $statusCode: ${response.statusLine.reasonPhrase}")
                }
            }
        } catch (e: IOException) {
            throw IOException(
                "Could not connect to Defold engine. Make sure the game is running from IntelliJ", e
            )
        }
    }

    /**
     * Creates a proper protobuf Resource$Reload message.
     * Based on resource_ddf.proto: message Reload { repeated string resources = 1; }
     */
    private fun createProtobufReloadPayload(resourcePaths: List<String>): ByteArray {
        // Simple protobuf encoding for: message Reload { repeated string resources = 1; }
        val output = mutableListOf<Byte>()

        for (path in resourcePaths) {
            val pathBytes = path.toByteArray(UTF_8)

            // Field 1 (resources), wire type 2 (length-delimited)
            // Field number 1 << 3 | wire_type = 1 << 3 | 2 = 0x0A
            output.add(0x0A.toByte())

            // Length of string
            encodeVarint(pathBytes.size, output)

            // String bytes
            output.addAll(pathBytes.toList())
        }

        return output.toByteArray()
    }

    /**
     * Encode a varint (variable-length integer) as used in protobuf
     */
    private fun encodeVarint(value: Int, output: MutableList<Byte>) {
        var v = value
        while (v >= 0x80) {
            output.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        output.add((v and 0x7F).toByte())
    }

    private fun <T> executeRequest(request: HttpUriRequest, block: (CloseableHttpResponse) -> T): T {
        httpClient.execute(request).use { response ->
            if (request is HttpEntityEnclosingRequestBase) {
                request.entity?.content?.close()
            }

            try {
                return block(response)
            } finally {
                EntityUtils.consumeQuietly(response.entity)
            }
        }
    }

    private fun findActiveConsole(): ConsoleView? =
        RunContentManager.getInstance(project).selectedContent?.executionConsole as? ConsoleView

    private fun ConsoleView?.appendToConsole(message: String, type: ConsoleViewContentType = NORMAL_OUTPUT) {
        this?.print("[HotReload] $message\n", type)
    }
}

data class BuildArtifact(
    val normalizedPath: String,
    val compiledPath: String,
    val file: File,
    val etag: String
)
