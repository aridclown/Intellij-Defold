package com.aridclown.intellij.defold.hotreload

import com.aridclown.intellij.defold.*
import com.aridclown.intellij.defold.DefoldConstants.ARTIFACT_MAP_FILE
import com.aridclown.intellij.defold.DefoldConstants.BUILD_CACHE_FOLDER
import com.aridclown.intellij.defold.DefoldProjectService.Companion.ensureConsole
import com.aridclown.intellij.defold.DefoldProjectService.Companion.findActiveConsole
import com.aridclown.intellij.defold.EngineDiscoveryService.Companion.getEngineDiscoveryService
import com.aridclown.intellij.defold.EngineEndpoint
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.aridclown.intellij.defold.util.SimpleHttpClient
import com.aridclown.intellij.defold.util.printError
import com.aridclown.intellij.defold.util.printInfo
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Duration.ofSeconds
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.readBytes

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
class HotReloadService(
    private val project: Project
) {
    companion object {
        private const val RELOAD_ENDPOINT = "/post/@resource/reload"
        private val HOT_RELOAD_EXTENSIONS = setOf("script", "lua", "gui_script", "go")
        private val KNOWN_BUILD_CONFIG_SEGMENTS = setOf("default", "debug", "release", "profile")
        private const val BUILD_TIMEOUT_SECONDS = 30L

        fun Project.hotReloadProjectService(): HotReloadService = service<HotReloadService>()
    }

    private val artifactsByNormalizedPath = mutableMapOf<String, BuildArtifact>()
    private val artifactCacheByCompiledPath = mutableMapOf<String, CachedArtifact>()
    private var runtime: HotReloadDependencies = createDefaultDependencies()

    init {
        loadArtifactCache()
    }

    suspend fun performHotReload() {
        val console = runtime.obtainConsole()
        val endpoints = runtime.ensureReachableEngines(console)
        if (endpoints.isEmpty()) return

        return try {
            // Ensure artifacts are cached
            artifactsByNormalizedPath.ifEmpty(::refreshBuildArtifacts)

            // Capture current artifacts before rebuild
            val oldArtifacts = artifactsByNormalizedPath.toMap()

            // Build the project to get updated resources
            val buildSuccess = runtime.buildProject(console)
            if (!buildSuccess) {
                console.printInfo("Build failed, cannot perform hot reload")
                return
            }

            // Update artifacts for all build outputs
            refreshBuildArtifacts()

            // Find resources that actually changed by comparing ETags
            val changedArtifacts =
                findChangedArtifacts(oldArtifacts).ifEmpty {
                    return // No resource changes detected after build
                }

            // Create a standard protobuf payload as expected by Defold engine
            val payload = createProtobufReloadPayload(changedArtifacts)

            // Send the reload command to the engine with changed resource paths
            endpoints.forEach { endpoint -> runtime.sendResourceReload(endpoint, payload) }
        } catch (e: Exception) {
            console.printError("Hot reload failed: ${e.message}")
        }
    }

    fun hasReachableEngine(): Boolean = resolveEngineEndpoints().isNotEmpty()

    internal fun refreshBuildArtifacts() {
        val basePath = project.basePath?.let(Path::of)
        val defaultBuildDir = basePath?.resolve("build")?.resolve("default")
        if (defaultBuildDir == null || defaultBuildDir.notExists()) {
            artifactsByNormalizedPath.clear()
            artifactCacheByCompiledPath.clear()
            saveArtifactCache()
            return
        }

        val existingCache = artifactCacheByCompiledPath.toMutableMap()
        val updatedCache = mutableMapOf<String, CachedArtifact>()

        artifactsByNormalizedPath.clear()

        Files.walk(defaultBuildDir).use { paths ->
            paths.filter { Files.isRegularFile(it) }.forEach { path ->
                val compiledPath = "/" + FileUtil.toSystemIndependentName(defaultBuildDir.relativize(path).toString())
                val normalizedPath = normalizeCompiledPath(compiledPath)
                val size = Files.size(path)
                val lastModified = Files.getLastModifiedTime(path).toMillis()

                val cached = existingCache[compiledPath]
                val etag =
                    if (cached != null && cached.size == size && cached.lastModified == lastModified) {
                        cached.etag
                    } else {
                        calculateEtag(path)
                    }

                updatedCache[compiledPath] = CachedArtifact(compiledPath, size, lastModified, etag)
                artifactsByNormalizedPath[normalizedPath] = BuildArtifact(normalizedPath, compiledPath, etag)
            }
        }

        artifactCacheByCompiledPath.apply {
            clear()
            putAll(updatedCache)
        }
        saveArtifactCache()
    }

    private fun calculateEtag(file: Path): String {
        val bytes = file.readBytes()
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    internal fun normalizeCompiledPath(compiledPath: String): String {
        // Remove the leading slash and normalize path separators
        val trimmed = compiledPath.trimStart('/')

        // For Defold, compiled paths in the build directory are structured as:
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

    internal fun findChangedArtifacts(oldArtifacts: Map<String, BuildArtifact>): List<BuildArtifact> = artifactsByNormalizedPath.values.filter { artifact ->
        val old = oldArtifacts[artifact.normalizedPath] ?: return@filter false
        old.etag != artifact.etag && isHotReloadable(artifact.normalizedPath)
    }

    private fun resolveEngineEndpoints(): List<EngineEndpoint> = project.getEngineDiscoveryService().currentEndpoints()

    private fun isEngineReachable(
        endpoint: EngineEndpoint,
        console: ConsoleView?
    ): Boolean = try {
        // Try to access the engine info endpoint to check its capabilities
        val pingUrl = "http://${endpoint.address}:${endpoint.port}/ping"
        val response = SimpleHttpClient.get(pingUrl)
        response.code in 200..299
    } catch (e: Exception) {
        console?.printError("Engine info check failed: ${e.message}")
        false
    }

    /**
     * Creates a proper protobuf `Resource$Reload` message. Based on
     * [resource_ddf.proto](https://github.com/defold/defold/blob/dev/engine/resource/proto/resource/resource_ddf.proto):
     *
     * ```
     * message Reload { repeated string resources = 1; }
     * ```
     */
    internal fun createProtobufReloadPayload(changedArtifacts: List<BuildArtifact>): ByteArray {
        /**
         * Encode a varint (variable-length integer) as used in protobuf
         */
        fun MutableList<Byte>.encodeVarint(value: Int) {
            var v = value
            while (v >= 0x80) {
                add(((v and 0x7F) or 0x80).toByte())
                v = v ushr 7
            }
            add((v and 0x7F).toByte())
        }

        return buildList {
            changedArtifacts.forEach {
                val pathBytes = it.normalizedPath.toByteArray(UTF_8)

                // Field 1 (resources), wire type 2 (length-delimited)
                // Field number 1 << 3 | wire_type = 1 << 3 | 2 = 0x0A
                add(0x0A.toByte())

                // Length of string
                encodeVarint(pathBytes.size)

                // String bytes
                addAll(pathBytes.toList())
            }
        }.toByteArray()
    }

    private fun loadArtifactCache() {
        val cacheFile = artifactCacheFile() ?: return
        if (cacheFile.notExists()) {
            artifactCacheByCompiledPath.clear()
            return
        }

        runCatching {
            artifactCacheByCompiledPath.clear()
            Files.newBufferedReader(cacheFile).use { reader ->
                reader.lineSequence().forEach { line ->
                    val parts = line.split('|')
                    if (parts.size == 4) {
                        val compiledPath = parts[0]
                        val size = parts[1].toLongOrNull()
                        val lastModified = parts[2].toLongOrNull()
                        val etag = parts[3]
                        if (size != null && lastModified != null) {
                            artifactCacheByCompiledPath[compiledPath] =
                                CachedArtifact(
                                    compiledPath,
                                    size,
                                    lastModified,
                                    etag
                                )
                        }
                    }
                }
            }
        }.onFailure {
            artifactCacheByCompiledPath.clear()
        }
    }

    private fun saveArtifactCache() {
        val cacheFile = artifactCacheFile() ?: return
        if (artifactCacheByCompiledPath.isEmpty()) {
            if (cacheFile.exists()) {
                cacheFile.deleteIfExists()
            }
            return
        }

        runCatching {
            cacheFile.parent?.let(Files::createDirectories)
            Files.newBufferedWriter(cacheFile).use { writer ->
                artifactCacheByCompiledPath.keys
                    .sorted()
                    .mapNotNull { artifactCacheByCompiledPath[it] }
                    .forEach { entry ->
                        writer.appendLine(
                            listOf(
                                entry.compiledPath,
                                entry.size.toString(),
                                entry.lastModified.toString(),
                                entry.etag
                            ).joinToString("|")
                        )
                    }
            }
        }.onFailure {
            cacheFile.deleteIfExists()
        }
    }

    private fun artifactCacheFile(): Path? {
        val basePath = project.basePath ?: return null

        return Paths
            .get(basePath, "build")
            .resolve(BUILD_CACHE_FOLDER)
            .let { Files.createDirectories(it) }
            .resolve(ARTIFACT_MAP_FILE)
    }

    @TestOnly
    internal fun setArtifactsForTesting(artifacts: Map<String, BuildArtifact>) {
        artifactsByNormalizedPath.apply {
            clear()
            putAll(artifacts)
        }
    }

    @TestOnly
    internal fun currentArtifactsForTesting(): Map<String, BuildArtifact> = artifactsByNormalizedPath.toMap()

    @TestOnly
    internal fun setDependenciesForTesting(dependencies: HotReloadDependencies) {
        runtime = dependencies
    }

    private fun createDefaultDependencies(): HotReloadDependencies = object : HotReloadDependencies {
        override fun obtainConsole(): ConsoleView = project.findActiveConsole() ?: project.ensureConsole("Defold Hot Reload")

        override fun ensureReachableEngines(console: ConsoleView): List<EngineEndpoint> {
            val reachable = resolveEngineEndpoints().filter { isEngineReachable(it, console) }
            if (reachable.isEmpty()) {
                console.printError("Defold engine not reachable. Make sure the game is running from IntelliJ")
            }
            return reachable
        }

        override suspend fun buildProject(console: ConsoleView): Boolean {
            val defoldService = project.getService(DefoldProjectService::class.java)
            val config =
                defoldService.editorConfig ?: run {
                    console.printError("Defold configuration not found")
                    return false
                }

            val processExecutor = ProcessExecutor(console)
            val builder = ProjectBuilder(processExecutor)

            val buildResult = withTimeoutOrNull(BUILD_TIMEOUT_SECONDS * 1000) {
                builder.buildProject(
                    request = BuildRequest(project, config)
                )
            } ?: run {
                console.printError("Build timed out after ${BUILD_TIMEOUT_SECONDS}s")
                return false
            }

            if (buildResult.isSuccess) {
                return true
            }

            buildResult.exceptionOrNull()?.let { throwable ->
                if (throwable !is BuildProcessFailedException) {
                    val message = throwable.message ?: throwable.javaClass.simpleName
                    console.printError("Build failed: $message")
                }
            }
            return false
        }

        override fun sendResourceReload(
            endpoint: EngineEndpoint,
            payload: ByteArray
        ) {
            val url = "http://${endpoint.address}:${endpoint.port}$RELOAD_ENDPOINT"

            try {
                val response =
                    SimpleHttpClient.postBytes(
                        url = url,
                        body = payload,
                        contentType = "application/x-protobuf",
                        timeout = ofSeconds(5)
                    )
                if (response.code !in 200..299) {
                    throw IOException("Engine reload request failed with status ${response.code}")
                }
            } catch (e: IOException) {
                throw IOException(
                    "Could not connect to Defold engine. Make sure the game is running from IntelliJ",
                    e
                )
            }
        }
    }
}

internal interface HotReloadDependencies {
    fun obtainConsole(): ConsoleView

    fun ensureReachableEngines(console: ConsoleView): List<EngineEndpoint>

    suspend fun buildProject(console: ConsoleView): Boolean

    fun sendResourceReload(
        endpoint: EngineEndpoint,
        payload: ByteArray
    )
}

data class BuildArtifact(
    val normalizedPath: String,
    val compiledPath: String,
    val etag: String
)

data class CachedArtifact(
    val compiledPath: String,
    val size: Long,
    val lastModified: Long,
    val etag: String
)
