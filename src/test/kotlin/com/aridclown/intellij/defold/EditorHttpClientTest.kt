package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.util.SimpleHttpClient
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class EditorHttpClientTest {
    @AfterEach
    fun tearDown() {
        unmockkObject(SimpleHttpClient)
    }

    @Test
    fun `connect returns client when command list available`() {
        val projectDir = Files.createTempDirectory("editorTest")
        writePortFile(projectDir, "8090")
        mockkObject(SimpleHttpClient)
        every { SimpleHttpClient.get(any(), any()) } returns SimpleHttpClient.SimpleHttpResponse(200, "{\"build\":\"Build\"}")

        val client = EditorHttpClient.connect(projectDir.toString())

        assertThat(client).isNotNull
        assertThat(client!!.supports("build")).isTrue
    }

    @Test
    fun `connect returns null when endpoint fails`() {
        val projectDir = Files.createTempDirectory("editorTest2")
        writePortFile(projectDir, "8091")
        mockkObject(SimpleHttpClient)
        every { SimpleHttpClient.get(any(), any()) } returns SimpleHttpClient.SimpleHttpResponse(500, "")

        assertThat(EditorHttpClient.connect(projectDir.toString())).isNull()
    }

    @Test
    fun `connect returns null when body missing`() {
        val projectDir = Files.createTempDirectory("editorTest3")
        writePortFile(projectDir, "8092")
        mockkObject(SimpleHttpClient)
        every { SimpleHttpClient.get(any(), any()) } returns SimpleHttpClient.SimpleHttpResponse(200, null)

        assertThat(EditorHttpClient.connect(projectDir.toString())).isNull()
    }

    @Test
    fun `sendCommand returns true on accepted command`() {
        val client = EditorHttpClientTestFactory.create()
        mockkObject(SimpleHttpClient)
        every { SimpleHttpClient.postBytes(any(), any(), any(), any()) } returns SimpleHttpClient.SimpleHttpResponse(202)

        assertThat(client.sendCommand("build")).isTrue
    }

    @Test
    fun `sendCommand returns true for any successful response`() {
        val client = EditorHttpClientTestFactory.create()
        mockkObject(SimpleHttpClient)
        every { SimpleHttpClient.postBytes(any(), any(), any(), any()) } returns SimpleHttpClient.SimpleHttpResponse(204)

        assertThat(client.sendCommand("build")).isTrue
    }

    @Test
    fun `sendCommand returns false on rejection`() {
        val client = EditorHttpClientTestFactory.create()
        mockkObject(SimpleHttpClient)
        every { SimpleHttpClient.postBytes(any(), any(), any(), any()) } returns SimpleHttpClient.SimpleHttpResponse(400)

        assertThat(client.sendCommand("build")).isFalse
    }

    private fun writePortFile(projectDir: Path, value: String) {
        val internalDir = projectDir.resolve(".internal")
        Files.createDirectories(internalDir)
        Files.writeString(internalDir.resolve("editor.port"), value)
    }
}
