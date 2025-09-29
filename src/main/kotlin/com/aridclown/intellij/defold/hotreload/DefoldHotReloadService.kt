package com.aridclown.intellij.defold.hotreload

import com.aridclown.intellij.defold.DefoldProjectBuilder
import com.aridclown.intellij.defold.DefoldProjectService
import com.aridclown.intellij.defold.engine.DefoldEngineDiscoveryService
import com.aridclown.intellij.defold.engine.DefoldEngineEndpoint
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
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
        private const val RELOAD_ENDPOINT = "/post/@resource/reload"
        private const val HOT_RELOAD_SERVER_PORT = 9999 // Our HTTP server port
        private val HOT_RELOAD_EXTENSIONS = setOf("script", "lua", "gui_script", "go")
        private val KNOWN_BUILD_CONFIG_SEGMENTS = setOf("default", "debug", "release", "profile")
        private val KNOWN_BUILD_PLATFORM_SEGMENTS = setOf(
            "x86_64-osx",
            "x86_64-linux",
            "x86_64-win32",
            "arm64-osx",
            "arm64-ios",
            "armv7-ios",
            "armv7-android",
            "arm64-android",
            "js-web",
            "wasm-web"
        )
        private const val BUILD_TIMEOUT_SECONDS = 30L

        fun Project.hotReloadProjectService(): DefoldHotReloadService = service<DefoldHotReloadService>()
    }

    private var lastHotReloadTime: Long = 0
    private val httpClient = HttpClients.createDefault()
    private val artifactsByNormalizedPath = mutableMapOf<String, BuildArtifact>()
    private val artifactsByCompiledPath = mutableMapOf<String, BuildArtifact>()

    fun performHotReload(console: ConsoleView? = null): Boolean {
        val hotReloadConsole = console ?: findActiveConsole() ?: run {
            notifyError("Hot reload requires an active run or debug session.")
            return false
        }

        return try {
            logStep("Starting hot reload sequence", hotReloadConsole)

            ensureArtifactCachePrimed(hotReloadConsole)

            // Require a running engine before proceeding
            val endpoint = resolveEngineEndpoint()
            if (endpoint == null) {
                val errorMsg =
                    "Could not determine Defold engine service port. Launch the engine from IntelliJ so its logs can be parsed, then try again."
                hotReloadConsole.print("$errorMsg\n", ConsoleViewContentType.ERROR_OUTPUT)
                println("[HotReload] Engine endpoint unavailable from discovery service")
                notifyError(errorMsg)
                return false
            }
            logStep(
                "Engine endpoint resolved: ${endpoint.address}:${endpoint.port} (log=${endpoint.logPort ?: "unknown"})",
                hotReloadConsole
            )

            if (!isEngineReachable(endpoint, console)) {
                val errorMsg =
                    "Defold engine not reachable at ${endpoint.address}:${endpoint.port}. Please make sure the game is running."
                hotReloadConsole.print("$errorMsg\n", ConsoleViewContentType.ERROR_OUTPUT)
                println("[HotReload] Engine not reachable at ${endpoint.address}:${endpoint.port}")
                notifyError(errorMsg)
                return false
            }
            logStep("Engine responded to reachability probe", hotReloadConsole)

            // 2. Capture current artifacts before rebuild
            val oldArtifacts = artifactsByNormalizedPath.toMap()
            logStep("Captured ${oldArtifacts.size} previously compiled artifacts", hotReloadConsole)

            // 3. Build the project to get updated resources  
            logStep("Launching Defold build", hotReloadConsole)
            val buildSuccess = buildProject(hotReloadConsole)
            if (!buildSuccess) {
                hotReloadConsole.print("Build failed, cannot perform hot reload\n", ConsoleViewContentType.ERROR_OUTPUT)
                println("[HotReload] Defold build failed; aborting hot reload")
                return false
            }
            logStep("Defold build completed", hotReloadConsole)

            // 4. Update artifacts for all build outputs
            refreshBuildArtifacts()
            logStep("Indexed ${artifactsByNormalizedPath.size} artifacts after build", hotReloadConsole)

            // 5. Find resources that actually changed by comparing ETags
            val changedArtifacts = findChangedArtifacts(oldArtifacts, artifactsByNormalizedPath)
            if (changedArtifacts.isEmpty()) {
                logStep("No resource changes detected after build", hotReloadConsole)
                notifyInfo("No resources to reload")
                return true
            }

            logChangedArtifacts(changedArtifacts, hotReloadConsole)

            // 8. Send reload command to engine with changed resource paths
            val resourcePaths = changedArtifacts.map { it.normalizedPath }
            logStep("Sending reload for paths: ${resourcePaths.joinToString()}", hotReloadConsole)
            sendResourceReloadToEngine(endpoint, resourcePaths, hotReloadConsole)

            // 9. Update timestamp and notify success
            lastHotReloadTime = System.currentTimeMillis()
            logStep("Hot reload command submitted in $lastHotReloadTime ms; awaiting engine fetch", hotReloadConsole)
            notifySuccess("Reloaded ${resourcePaths.size} resources")
            true
        } catch (e: Exception) {
            val message = e.message ?: e::class.java.simpleName
            hotReloadConsole.print("Hot reload failed: $message\n", ConsoleViewContentType.ERROR_OUTPUT)
            println("[HotReload] Hot reload failed: $message")
            e.printStackTrace()
            notifyError("Hot reload failed: $message")
            false
        }
    }

    private fun buildProject(console: ConsoleView): Boolean {
        val defoldService = project.getService(DefoldProjectService::class.java)
        val config = defoldService.editorConfig ?: run {
            console.print("Error: Defold configuration not found\n", ConsoleViewContentType.ERROR_OUTPUT)
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
            artifactsByCompiledPath.clear()
            return
        }

        artifactsByNormalizedPath.clear()
        artifactsByCompiledPath.clear()

        buildDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val compiledPath = "/" + file.relativeTo(buildDir).path.replace(File.separatorChar, '/')
                val normalizedPath = normalizeCompiledPath(compiledPath)
                val etag = calculateEtag(file)
                val artifact = BuildArtifact(normalizedPath, compiledPath, file, etag)

                artifactsByNormalizedPath[normalizedPath] = artifact
                artifactsByCompiledPath[compiledPath] = artifact
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

        if (parts[index] in KNOWN_BUILD_PLATFORM_SEGMENTS) {
            index++
        }

        if (index < parts.size && parts[index] in KNOWN_BUILD_CONFIG_SEGMENTS) {
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

    private fun logChangedArtifacts(artifacts: List<BuildArtifact>, console: ConsoleView?) {
        val details = artifacts.joinToString { "${it.normalizedPath} (compiled ${it.compiledPath})" }
        logStep("Changed compiled resources: $details", console)
    }

    private fun ensureArtifactCachePrimed(console: ConsoleView?) {
        if (artifactsByNormalizedPath.isEmpty()) {
            logStep("Artifact cache empty, priming from existing build outputs", console)
            refreshBuildArtifacts()
        }
    }

    private fun findChangedArtifacts(
        oldArtifacts: Map<String, BuildArtifact>,
        newArtifacts: Map<String, BuildArtifact>
    ): List<BuildArtifact> {
        return newArtifacts.values
            .asSequence()
            .filter { artifact ->
                val old = oldArtifacts[artifact.normalizedPath] ?: return@filter false
                old.etag != artifact.etag && isHotReloadable(artifact.normalizedPath)
            }
            .toList()
    }

    private fun resolveEngineEndpoint(): DefoldEngineEndpoint? {
        val discovery = project.getService(DefoldEngineDiscoveryService::class.java) ?: return null
        return discovery.currentEndpoint()
    }

    private fun isEngineReachable(endpoint: DefoldEngineEndpoint, console: ConsoleView?): Boolean = try {
        // Try to access the engine info endpoint to check its capabilities
        val infoUrl = "http://${endpoint.address}:${endpoint.port}/info"
        val request = org.apache.http.client.methods.HttpGet(infoUrl)

        val requestConfig = org.apache.http.client.config.RequestConfig.custom()
            .setConnectTimeout(2000)
            .setSocketTimeout(3000)
            .build()
        request.config = requestConfig

        executeRequest(request) { response ->
            val responseBody = response.entity?.content?.bufferedReader()?.use { it.readText() } ?: ""
            logStep("Engine info: $responseBody", console)

            response.statusLine.statusCode in 200..299
        }
    } catch (e: Exception) {
        logStep("Engine info check failed (may not be a debug build): ${e.message}", console)
        false
    }

    private fun sendResourceReloadToEngine(
        endpoint: DefoldEngineEndpoint,
        resourcePaths: List<String>,
        console: ConsoleView?
    ) {
        val url = "http://${endpoint.address}:${endpoint.port}$RELOAD_ENDPOINT"

        // Create a standard protobuf payload as expected by Defold engine
        val payload = createProtobufReloadPayload(resourcePaths)

        logStep("Reload payload resources: ${resourcePaths.joinToString()}", console)
        logStep("Reload payload bytes (hex): ${payload.joinToString(separator = " ") { "%02X".format(it) }}", console)

        try {
            val request = HttpPost(url)
            request.entity = ByteArrayEntity(payload)
            request.setHeader("Content-Type", "application/x-protobuf")
            logStep("Sending reload POST to $url", console)

            // Set connection timeout to fail fast if the engine isn't running
            val requestConfig = org.apache.http.client.config.RequestConfig.custom()
                .setConnectTimeout(2000) // 2 seconds
                .setSocketTimeout(5000)  // 5 seconds
                .setExpectContinueEnabled(false)
                .build()
            request.config = requestConfig

            executeRequest(request) { response ->
                val statusCode = response.statusLine.statusCode
                logStep("Engine response status=$statusCode", console)

                if (statusCode !in 200..299) {
                    throw IOException("Engine reload request failed with status $statusCode: ${response.statusLine.reasonPhrase}")
                }
            }
        } catch (e: IOException) {
            throw IOException(
                """
                    Could not connect to Defold engine at ${endpoint.address}:${endpoint.port}. Make sure the game is running and the engine HTTP server is enabled.
                """.trimIndent(), e
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
            val pathBytes = path.toByteArray(StandardCharsets.UTF_8)

            // Field 1 (resources), wire type 2 (length-delimited)
            // Field number 1 << 3 | wire_type = 1 << 3 | 2 = 0x0A
            output.add(0x0A.toByte())

            // Length of string
            encodeVarint(pathBytes.size, output)

            // String bytes
            output.addAll(pathBytes.toList())
        }

        val result = output.toByteArray()
        println("[HotReload] Protobuf payload for ${resourcePaths.size} resources: ${result.size} bytes")
        println("[HotReload] Payload hex: ${result.joinToString(" ") { "%02X".format(it) }}")
        return result
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

    private fun notifySuccess(message: String) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Defold")
                .createNotification("Hot reload", message, NotificationType.INFORMATION)
                .notify(project)
        }
    }

    private fun notifyInfo(message: String) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Defold")
                .createNotification("Hot reload", message, NotificationType.INFORMATION)
                .notify(project)
        }
    }

    private fun notifyError(message: String) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Defold")
                .createNotification("Hot reload", message, NotificationType.ERROR)
                .notify(project)
        }
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

    private fun logStep(message: String, console: ConsoleView?) {
        console?.print("[HotReload] $message\n", ConsoleViewContentType.NORMAL_OUTPUT)
    }
}

data class BuildArtifact(
    val normalizedPath: String,
    val compiledPath: String,
    val file: File,
    val etag: String
)
