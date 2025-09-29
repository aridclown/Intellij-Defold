package com.aridclown.intellij.defold.hotreload

import com.aridclown.intellij.defold.DefoldProjectBuilder
import com.aridclown.intellij.defold.DefoldProjectService
import com.aridclown.intellij.defold.engine.DefoldEngineDiscoveryService
import com.aridclown.intellij.defold.engine.DefoldEngineEndpoint
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
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
@Service(Service.Level.PROJECT)
class DefoldHotReloadService(private val project: Project) : Disposable {

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
    }

    private var lastHotReloadTime: Long = 0
    private val httpClient = HttpClients.createDefault()
    private var configEndpointSupported: Boolean? = null
    private var debugEndpointSupported: Boolean? = null
    private var hotReloadInstructionsLogged: Boolean = false
    private val artifactsByNormalizedPath = mutableMapOf<String, BuildArtifact>()
    private val artifactsByCompiledPath = mutableMapOf<String, BuildArtifact>()
    private var hotReloadServer: DefoldHotReloadServer? = null

    fun performHotReload(console: ConsoleView? = null): Boolean {
        return try {
            logStep("Starting hot reload sequence", console)

            // 1. Start HTTP server if not already running
            ensureHttpServerRunning()
            logStep("HTTP server ensured on port $HOT_RELOAD_SERVER_PORT", console)

            ensureArtifactCachePrimed(console)

            // 2. Capture current artifacts before rebuild
            val oldArtifacts = artifactsByNormalizedPath.toMap()
            logStep("Captured ${oldArtifacts.size} previously compiled artifacts", console)

            // 3. Build the project to get updated resources  
            logStep("Launching Defold build", console)
            val buildSuccess = buildProject(console)
            if (!buildSuccess) {
                console?.print("Build failed, cannot perform hot reload\n", ConsoleViewContentType.ERROR_OUTPUT)
                println("[HotReload] Defold build failed; aborting hot reload")
                return false
            }
            logStep("Defold build completed", console)

            // 4. Update artifacts for all build outputs
            refreshBuildArtifacts()
            logStep("Indexed ${artifactsByNormalizedPath.size} artifacts after build", console)

            // 5. Find resources that actually changed by comparing ETags
            val changedArtifacts = findChangedArtifacts(oldArtifacts, artifactsByNormalizedPath)
            if (changedArtifacts.isEmpty()) {
                logStep("No resource changes detected after build", console)
                notifyInfo("No resources to reload")
                return true
            }

            logChangedArtifacts(changedArtifacts, console)

            // 6. Resolve current engine endpoint
            val endpoint = resolveEngineEndpoint()
            if (endpoint == null) {
                val errorMsg = "Could not determine Defold engine service port. Launch the engine from IntelliJ so its logs can be parsed, then try again."
                console?.print("$errorMsg\n", ConsoleViewContentType.ERROR_OUTPUT)
                println("[HotReload] Engine endpoint unavailable from discovery service")
                notifyError(errorMsg)
                return false
            }
            logStep("Engine endpoint resolved: ${endpoint.address}:${endpoint.port} (log=${endpoint.logPort ?: "unknown"})", console)

            // 7. Check if engine is reachable and properly configured for hot reload
            if (!isEngineReachable(endpoint)) {
                val errorMsg = "Defold engine not reachable at ${endpoint.address}:${endpoint.port}. Please make sure the game is running."
                console?.print("$errorMsg\n", ConsoleViewContentType.ERROR_OUTPUT)
                println("[HotReload] Engine not reachable at ${endpoint.address}:${endpoint.port}")
                notifyError(errorMsg)
                return false
            }
            logStep("Engine responded to reachability probe", console)
            
            // Check if engine supports hot reload (development build)
            if (!isEngineHotReloadCapable(endpoint, console)) {
                val errorMsg = "Engine does not support hot reload. Please ensure you're running a DEBUG build of your Defold game with hot reload enabled."
                console?.print("$errorMsg\n", ConsoleViewContentType.ERROR_OUTPUT)
                println("[HotReload] Engine is not hot reload capable")
                notifyError(errorMsg)
                return false
            }

            // 8. Send reload command to engine with changed resource paths
            val resourcePaths = changedArtifacts.map { it.normalizedPath }
            logStep("Sending reload for paths: ${resourcePaths.joinToString()}", console)
            
            // CRITICAL: Also send the HTTP server URL so engine knows where to fetch resources
            sendResourceReloadToEngine(endpoint, resourcePaths, console)
            
            // Notify engine of our resource server URL via environment or debug message
            notifyEngineOfResourceServer(endpoint, console)

            // 9. Update timestamp and notify success
            lastHotReloadTime = System.currentTimeMillis()
            logStep("Reload command submitted; awaiting engine fetch", console)
            notifySuccess("Reloaded ${resourcePaths.size} resources")
            true

        } catch (e: Exception) {
            val message = e.message ?: e::class.java.simpleName
            console?.print("Hot reload failed: $message\n", ConsoleViewContentType.ERROR_OUTPUT)
            println("[HotReload] Hot reload failed: $message")
            e.printStackTrace()
            notifyError("Hot reload failed: $message")
            false
        }
    }

    private fun ensureHttpServerRunning() {
        if (hotReloadServer == null) {
            val buildDir = File(project.basePath, "build")
            hotReloadServer = DefoldHotReloadServer(
                buildDir,
                HOT_RELOAD_SERVER_PORT,
                artifactsByCompiledPath,
                artifactsByNormalizedPath
            )
            hotReloadServer?.start()
            
            // Critical: Configure the engine to fetch resources from our server
            configureEngineResourceServer()
        }
    }
    
    /**
     * Configure the engine to fetch resources from our HTTP server.
     * This is crucial - without this, the engine won't know where to fetch updated resources.
     */
    private fun configureEngineResourceServer() {
        val endpoint = resolveEngineEndpoint()
        if (endpoint != null) {
            if (configEndpointSupported == false) {
                return
            }
            try {
                val configUrl = "http://${endpoint.address}:${endpoint.port}/config"
                val resourceServerUrl = "http://localhost:$HOT_RELOAD_SERVER_PORT/build"
                
                logStep("Configuring engine to fetch resources from: $resourceServerUrl", null)
                
                // Send configuration to engine about our resource server
                val request = HttpPost(configUrl)
                val configPayload = """{"resource_server": "$resourceServerUrl"}""".toByteArray()
                request.entity = ByteArrayEntity(configPayload)
                request.setHeader("Content-Type", "application/json")
                
                val requestConfig = org.apache.http.client.config.RequestConfig.custom()
                    .setConnectTimeout(1000)
                    .setSocketTimeout(2000)
                    .build()
                request.config = requestConfig
                
                try {
                    val status = executeRequest(request) { response ->
                        response.statusLine.statusCode
                    }
                    when {
                        status in 200..299 -> {
                            configEndpointSupported = true
                            println("[HotReload] Successfully configured engine resource server")
                        }
                        else -> {
                            configEndpointSupported = false
                            println("[HotReload] Engine config response: $status")
                        }
                    }
                } catch (e: Exception) {
                    configEndpointSupported = false
                    println("[HotReload] Engine doesn't support /config endpoint (this is normal for older engines)")
                }
                
            } catch (e: Exception) {
                println("[HotReload] Could not configure engine resource server: ${e.message}")
            }
        }
    }

    private fun buildProject(console: ConsoleView?): Boolean {
        val defoldService = project.getService(DefoldProjectService::class.java)
        val config = defoldService.editorConfig ?: run {
            console?.print("Error: Defold configuration not found\n", ConsoleViewContentType.ERROR_OUTPUT)
            return false
        }
        
        val processExecutor = ProcessExecutor(console ?: createNullConsole())
        val builder = DefoldProjectBuilder(console ?: createNullConsole(), processExecutor)

        // Build project synchronously
        var buildSuccess = false
        val latch = CountDownLatch(1)

        val result = builder.buildProject(
            project = project,
            config = config,
            envData = EnvironmentVariablesData.DEFAULT,
            onBuildSuccess = {
                buildSuccess = true
                latch.countDown()
            },
            onBuildFailure = { _ ->
                buildSuccess = false
                latch.countDown()
            }
        )

        // Wait for build to complete
        result.onFailure {
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

    private fun isEngineReachable(endpoint: DefoldEngineEndpoint): Boolean {
        return try {
            val url = "http://${endpoint.address}:${endpoint.port}/"
            val request = org.apache.http.client.methods.HttpGet(url)

            println("[HotReload] Probing engine reachability at $url")
            
            // Quick check with short timeout
            val requestConfig = org.apache.http.client.config.RequestConfig.custom()
                .setConnectTimeout(1000) // 1 second
                .setSocketTimeout(2000)  // 2 seconds
                .build()
            request.config = requestConfig

            println("[HotReload] Sending reachability GET request to $url")
            executeRequest(request) { response ->
                val statusCode = response.statusLine.statusCode
                println("[HotReload] Engine reachability response: $statusCode")
                statusCode in 200..499
            }
        } catch (e: Exception) {
            println("[HotReload] Engine reachability check failed: ${e.message}")
            false
        }
    }

    private fun sendResourceReloadToEngine(
        endpoint: DefoldEngineEndpoint,
        resourcePaths: List<String>,
        console: ConsoleView?
    ) {
        val url = "http://${endpoint.address}:${endpoint.port}$RELOAD_ENDPOINT"
        
        // Create standard protobuf payload as expected by Defold engine
        val payload = createProtobufReloadPayload(resourcePaths)

        logStep("Reload payload resources: ${resourcePaths.joinToString()}", console)
        logStep("Reload payload bytes (hex): ${payload.joinToString(separator = " ") { "%02X".format(it) }}", console)

        try {
            val request = HttpPost(url)
            request.entity = ByteArrayEntity(payload)
            request.setHeader("Content-Type", "application/x-protobuf")
            logStep("Sending reload POST to $url", console)

            // Set connection timeout to fail fast if engine isn't running
            val requestConfig = org.apache.http.client.config.RequestConfig.custom()
                .setConnectTimeout(2000) // 2 seconds
                .setSocketTimeout(5000)  // 5 seconds
                .build()
            request.config = requestConfig

            executeRequest(request) { response ->
                val statusCode = response.statusLine.statusCode
                logStep("Engine response status=$statusCode", console)

                if (statusCode !in 200..299) {
                    throw IOException("Engine reload request failed with status $statusCode: ${response.statusLine.reasonPhrase}")
                }
            }
        } catch (e: java.net.ConnectException) {
            println("[HotReload] Connection error: ${e.message}")
            throw IOException("Could not connect to Defold engine at ${endpoint.address}:${endpoint.port}. " +
                    "Make sure the game is running and the engine HTTP server is enabled.")
        } catch (e: java.net.NoRouteToHostException) {
            println("[HotReload] No route to host: ${e.message}")
            throw IOException("No route to Defold engine at ${endpoint.address}:${endpoint.port}. " +
                    "Ensure the game is running locally.")
        } catch (e: org.apache.http.conn.HttpHostConnectException) {
            println("[HotReload] Host connect exception: ${e.message}")
            throw IOException("Defold engine not found at ${endpoint.address}:${endpoint.port}. " +
                    "Please start your Defold game first, then try hot reload again.")
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
     * Notify the engine about our resource server URL so it can fetch updated resources.
     * This is critical for hot reload to work - the engine must know where to get resources.
     */
    private fun notifyEngineOfResourceServer(endpoint: DefoldEngineEndpoint, console: ConsoleView?) {
        try {
            val resourceServerUrl = "http://localhost:$HOT_RELOAD_SERVER_PORT"
            if (debugEndpointSupported == false) {
                logHotReloadSetupInstructions(console)
                return
            }
            
            // Method 1: Try to set via debug message (if supported)
            val debugUrl = "http://${endpoint.address}:${endpoint.port}/debug"
            val debugMessage = """{"type":"set_resource_server","url":"$resourceServerUrl"}"""
            
            val request = HttpPost(debugUrl)
            request.entity = ByteArrayEntity(debugMessage.toByteArray())
            request.setHeader("Content-Type", "application/json")
            
            val requestConfig = org.apache.http.client.config.RequestConfig.custom()
                .setConnectTimeout(1000)
                .setSocketTimeout(2000)
                .build()
            request.config = requestConfig
            
            try {
                val status = executeRequest(request) { response ->
                    response.statusLine.statusCode
                }
                when {
                    status in 200..299 -> {
                        debugEndpointSupported = true
                        logStep("Successfully notified engine of resource server at: $resourceServerUrl", console)
                    }
                    else -> {
                        debugEndpointSupported = false
                        logStep("Engine debug endpoint response: $status", console)
                        logHotReloadSetupInstructions(console)
                    }
                }
            } catch (e: Exception) {
                if (debugEndpointSupported != false) {
                    logStep("Engine doesn't support debug configuration (expected for standard builds)", console)
                    debugEndpointSupported = false
                    logHotReloadSetupInstructions(console)
                }
            }
            
        } catch (e: Exception) {
            logStep("Could not notify engine of resource server: ${e.message}", console)
        }
    }

    /**
     * Check if the engine supports hot reload (i.e., was built in debug/development mode)
     */
    private fun isEngineHotReloadCapable(endpoint: DefoldEngineEndpoint, console: ConsoleView?): Boolean {
        return try {
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

                // If we can access /info, it's likely a debug build
                response.statusLine.statusCode in 200..299
            }
        } catch (e: Exception) {
            logStep("Engine info check failed (may not be a debug build): ${e.message}", console)
            
            // Try alternative: check if we can access the POST endpoint
            return try {
                val testUrl = "http://${endpoint.address}:${endpoint.port}/post/@system/test"
                val testRequest = org.apache.http.client.methods.HttpPost(testUrl)
                testRequest.entity = ByteArrayEntity(ByteArray(0))
                
                val testConfig = org.apache.http.client.config.RequestConfig.custom()
                    .setConnectTimeout(1000)
                    .setSocketTimeout(2000)
                    .build()
                testRequest.config = testConfig
                
                executeRequest(testRequest) { response ->
                    val hotReloadCapable = response.statusLine.statusCode != 404

                    if (!hotReloadCapable) {
                        logStep("Engine POST endpoints not available - likely a release build", console)
                    } else {
                        logStep("Engine POST endpoints available - debug build detected", console)
                    }

                    hotReloadCapable
                }
            } catch (e2: Exception) {
                logStep("Engine capability check failed: ${e2.message}", console)
                false
            }
        }
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

    private fun createNullConsole(): ConsoleView {
        return object : ConsoleView {
            override fun print(text: String, contentType: ConsoleViewContentType) {}
            override fun clear() {}
            override fun scrollTo(offset: Int) {}
            override fun attachToProcess(processHandler: com.intellij.execution.process.ProcessHandler) {}
            override fun setOutputPaused(value: Boolean) {}
            override fun isOutputPaused(): Boolean = false
            override fun hasDeferredOutput(): Boolean = false
            override fun performWhenNoDeferredOutput(runnable: Runnable) { runnable.run() }
            override fun setHelpId(helpId: String) {}
            override fun addMessageFilter(filter: com.intellij.execution.filters.Filter) {}
            override fun printHyperlink(hyperlinkText: String, info: com.intellij.execution.filters.HyperlinkInfo?) {}
            override fun getContentSize(): Int = 0
            override fun canPause(): Boolean = false
            override fun createConsoleActions(): Array<com.intellij.openapi.actionSystem.AnAction> = emptyArray()
            override fun allowHeavyFilters() {}
            override fun getComponent(): javax.swing.JComponent = javax.swing.JPanel()
            override fun getPreferredFocusableComponent(): javax.swing.JComponent? = null
            override fun dispose() {}
        }
    }

    private fun notifySuccess(message: String) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Defold")
                .createNotification("Hot Reload", message, NotificationType.INFORMATION)
                .notify(project)
        }
    }

    private fun notifyInfo(message: String) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Defold")
                .createNotification("Hot Reload", message, NotificationType.INFORMATION)
                .notify(project)
        }
    }

    private fun notifyError(message: String) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Defold")
                .createNotification("Hot Reload", message, NotificationType.ERROR)
                .notify(project)
        }
    }

    private fun logHotReloadSetupInstructions(console: ConsoleView?) {
        if (hotReloadInstructionsLogged) {
            return
        }

        hotReloadInstructionsLogged = true
        logStep("", console)
        logStep("============================================================================", console)
        logStep("CRITICAL: Hot Reload Setup Instructions", console)
        logStep("============================================================================", console)
        logStep("", console)
        logStep("For hot reload to work, your Defold game MUST be configured as follows:", console)
        logStep("", console)
        logStep("1. Build Type: Use DEBUG build (not Release)", console)
        logStep("   In game.project under [bootstrap]:", console)
        logStep("     debug_mode = 1", console)
        logStep("", console)
        logStep("2. Launch with hot reload support:", console)
        logStep("   Run with environment variable or command line argument:", console)
        logStep("     DEFOLD_HOT_RELOAD=1", console)
        logStep("   Or add to game.project:", console)
        logStep("     hot_reload = 1", console)
        logStep("", console)
        logStep("3. Resource Server URL (this plugin's HTTP server):", console)
        logStep("     http://localhost:$HOT_RELOAD_SERVER_PORT/build", console)
        logStep("", console)
        logStep("4. If using custom launch configuration, ensure HTTP server is enabled", console)
        logStep("   and listening on the port discovered by this plugin.", console)
        logStep("", console)
        logStep("============================================================================", console)
        logStep("", console)
    }

    private fun <T> executeRequest(request: HttpUriRequest, block: (CloseableHttpResponse) -> T): T {
        httpClient.execute(request).use { response ->
            try {
                return block(response)
            } finally {
                EntityUtils.consumeQuietly(response.entity)
            }
        }
    }

    private fun logStep(message: String, console: ConsoleView?) {
        println("[HotReload] $message")
        console?.print("[HotReload] $message\n", ConsoleViewContentType.NORMAL_OUTPUT)
    }

    override fun dispose() {
        hotReloadServer?.stop()
        hotReloadServer = null
    }
}

data class BuildArtifact(
    val normalizedPath: String,
    val compiledPath: String,
    val file: File,
    val etag: String
)
