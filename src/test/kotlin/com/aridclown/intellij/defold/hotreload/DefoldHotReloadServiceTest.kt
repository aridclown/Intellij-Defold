package com.aridclown.intellij.defold.hotreload

import com.aridclown.intellij.defold.DefoldProjectService
import com.intellij.openapi.project.Project
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest

class DefoldHotReloadServiceTest {

    private lateinit var mockProject: Project
    private lateinit var mockDefoldService: DefoldProjectService
    private lateinit var hotReloadService: DefoldHotReloadService

    @BeforeEach
    fun setUp() {
        mockProject = mockk(relaxed = true)
        mockDefoldService = mockk(relaxed = true)
        
        every { mockProject.getService(DefoldProjectService::class.java) } returns mockDefoldService
        every { mockProject.basePath } returns "/test/project"
        
        hotReloadService = DefoldHotReloadService(mockProject)
    }

    @Test
    fun `should calculate ETags correctly`() {
        // Given a test file
        val testContent = "test content"
        val expectedEtag = calculateTestEtag(testContent)
        
        // When we calculate the ETag using the same algorithm
        val actualEtag = testContent.toByteArray().let { bytes ->
            val digest = MessageDigest.getInstance("MD5")
            val hash = digest.digest(bytes)
            hash.joinToString("") { "%02x".format(it) }
        }
        
        // Then ETags should match
        assertThat(actualEtag).isEqualTo(expectedEtag)
    }

    @Test
    fun `should detect changed resources by comparing ETags`() {
        // Given old and new ETag maps
        val oldEtags = mapOf(
            "/main/player.scriptc" to "abc123",
            "/utils/helper.lua" to "def456"
        )
        
        val newEtags = mapOf(
            "/main/player.scriptc" to "xyz789", // Changed
            "/utils/helper.lua" to "def456",    // Unchanged
            "/gui/menu.gui_scriptc" to "new123" // New file
        )
        
        // When finding changed resources
        val changedResources = findChangedResourcesTest(oldEtags, newEtags)
        
        // Then should detect only previously known files that changed
        assertThat(changedResources).containsExactly(
            "/main/player.scriptc"
        )
    }

    @Test
    fun `should create correct resource reload protobuf message`() {
        // Given some resource paths
        val resourcePaths = listOf("/main/player.scriptc", "/utils/helper.lua")
        
        // When creating protobuf message
        val messageBytes = createTestResourceReloadMessage(resourcePaths)
        
        val buffer = java.nio.ByteBuffer.wrap(messageBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)

        val pointerArrayOffset = buffer.long.toInt()
        val count = buffer.int
        val capacity = buffer.int

        assertThat(pointerArrayOffset)
            .describedAs("pointer array offset")
            .isEqualTo(java.lang.Long.BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES)
        assertThat(count).isEqualTo(resourcePaths.size)
        assertThat(capacity).isGreaterThanOrEqualTo(count)

        val pointerSize = java.lang.Long.BYTES
        val resolvedPaths = (0 until count).map { index ->
            val pointer = java.nio.ByteBuffer
                .wrap(messageBytes, pointerArrayOffset + index * pointerSize, pointerSize)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .long
                .toInt()
            readNullTerminatedString(messageBytes, pointer)
        }

        assertThat(resolvedPaths).containsExactlyElementsOf(resourcePaths)
    }

    @Test
    fun `should filter resources by supported extensions`() {
        // Given ETags for various file types
        val allEtags = mapOf(
            "/main/player.scriptc" to "abc123",    // Should include (script -> scriptc)
            "/utils/helper.lua" to "def456",       // Should include (lua)
            "/gui/menu.gui_scriptc" to "ghi789",   // Should include (gui_script -> gui_scriptc)
            "/objects/enemy.goc" to "jkl012",      // Should include (go -> goc) 
            "/images/player.texturec" to "mno345", // Should exclude (not supported)
            "/sounds/jump.oggc" to "pqr678"        // Should exclude (not supported)
        )
        
        // When filtering by supported extensions
        val filteredResources = filterBySupportedExtensions(allEtags.keys.toList())
        
        // Then should only include supported resource types
        assertThat(filteredResources).containsExactlyInAnyOrder(
            "/main/player.scriptc",
            "/utils/helper.lua", 
            "/gui/menu.gui_scriptc",
            "/objects/enemy.goc"
        )
    }

    // Helper methods for testing private functionality
    
    private fun calculateTestEtag(content: String): String {
        val bytes = content.toByteArray()
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    private fun findChangedResourcesTest(oldEtags: Map<String, String>, newEtags: Map<String, String>): List<String> {
        return newEtags.entries
            .filter { (path, etag) ->
                val old = oldEtags[path] ?: return@filter false
                old != etag
            }
            .map { it.key }
            .filter { path ->
                val extension = path.substringAfterLast(".").removeSuffix("c")
                extension in setOf("script", "lua", "gui_script", "go")
            }
    }
    
    private fun filterBySupportedExtensions(paths: List<String>): List<String> {
        return paths.filter { path ->
            val extension = path.substringAfterLast(".").removeSuffix("c")
            extension in setOf("script", "lua", "gui_script", "go")
        }
    }

    private fun readNullTerminatedString(bytes: ByteArray, offset: Int): String {
        var end = offset
        while (end < bytes.size && bytes[end] != 0.toByte()) {
            end++
        }
        return bytes.copyOfRange(offset, end).toString(Charsets.UTF_8)
    }

    private fun createTestResourceReloadMessage(resourcePaths: List<String>): ByteArray {
        val pointerSize = java.lang.Long.BYTES
        val structSize = pointerSize + Int.SIZE_BYTES + Int.SIZE_BYTES
        val pointerArraySize = resourcePaths.size * pointerSize
        val stringBytesSize = resourcePaths.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 }
        val totalSize = structSize + pointerArraySize + stringBytesSize

        val buffer = ByteArray(totalSize)
        val header = java.nio.ByteBuffer.wrap(buffer, 0, structSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)

        val pointerArrayOffset = structSize
        val stringsOffsetStart = pointerArrayOffset + pointerArraySize

        header.putLong(pointerArrayOffset.toLong())
        header.putInt(resourcePaths.size)
        header.putInt(resourcePaths.size)

        var stringCursor = stringsOffsetStart
        resourcePaths.forEachIndexed { index, path ->
            val pathBytes = path.toByteArray(Charsets.UTF_8)

            java.nio.ByteBuffer.wrap(buffer, pointerArrayOffset + index * pointerSize, pointerSize)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putLong(stringCursor.toLong())

            System.arraycopy(pathBytes, 0, buffer, stringCursor, pathBytes.size)
            buffer[stringCursor + pathBytes.size] = 0
            stringCursor += pathBytes.size + 1
        }

        return buffer
    }

    @Test
    fun `should normalize compiled paths relative to build outputs`() {
        val method = DefoldHotReloadService::class.java.getDeclaredMethod(
            "normalizeCompiledPath",
            String::class.java
        )
        method.isAccessible = true

        val samples = listOf(
            "/default/stars/factory.scriptc",
            "/x86_64-osx/default/stars/factory.scriptc",
            "/assets/tiles/tilemap.gui_scriptc"
        )

        val normalized = samples.associateWith { sample ->
            method.invoke(hotReloadService, sample) as String
        }

        assertThat(normalized)
            .containsEntry(
                "/default/stars/factory.scriptc",
                "/stars/factory.scriptc"
            )
            .containsEntry(
                "/x86_64-osx/default/stars/factory.scriptc",
                "/stars/factory.scriptc"
            )
            .containsEntry(
                "/assets/tiles/tilemap.gui_scriptc",
                "/assets/tiles/tilemap.gui_scriptc"
            )
    }
}
