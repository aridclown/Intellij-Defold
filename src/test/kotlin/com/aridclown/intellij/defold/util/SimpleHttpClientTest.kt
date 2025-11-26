package com.aridclown.intellij.defold.util

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Tests for SimpleHttpClient - HTTP operations for hot reload and annotations.
 */
class SimpleHttpClientTest {
    private lateinit var server: MockWebServer

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `GET requests return response code and body`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("Hello, World!")
        )

        val url = server.url("/test").toString()
        val response = SimpleHttpClient.get(url)

        assertThat(response.code).isEqualTo(200)
        assertThat(response.body).isEqualTo("Hello, World!")
    }

    @Test
    fun `GET requests handle non-200 responses`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Not Found")
        )

        val url = server.url("/missing").toString()
        val response = SimpleHttpClient.get(url)

        assertThat(response.code).isEqualTo(404)
        assertThat(response.body).isEqualTo("Not Found")
    }

    @Test
    fun `POST requests send bytes with content type`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("Created")
        )

        val url = server.url("/upload").toString()
        val data = "test data".toByteArray()
        val response = SimpleHttpClient.postBytes(url, data, "application/octet-stream")

        assertThat(response.code).isEqualTo(201)
        assertThat(response.body).isEqualTo("Created")

        val recordedRequest = server.takeRequest()
        assertThat(recordedRequest.method).isEqualTo("POST")
        assertThat(recordedRequest.body.readUtf8()).isEqualTo("test data")
        assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo("application/octet-stream")
    }

    @Test
    fun `files can be downloaded to disk`() {
        val fileContent = "Downloaded file content"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fileContent)
        )

        val url = server.url("/file.txt").toString()
        val targetFile = tempDir.resolve("downloaded.txt")

        SimpleHttpClient.downloadToPath(url, targetFile)

        assertThat(targetFile).exists()
        assertThat(Files.readString(targetFile)).isEqualTo(fileContent)
    }

    @Test
    fun `download throws on HTTP error`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Server Error")
        )

        val url = server.url("/error").toString()
        val targetFile = tempDir.resolve("failed.txt")

        assertThatThrownBy {
            SimpleHttpClient.downloadToPath(url, targetFile)
        }.isInstanceOf(IOException::class.java)
            .hasMessageContaining("HTTP 500")
            .hasMessageContaining(url)
    }

    @Test
    fun `custom timeout can be specified`() {
        // Respond slowly
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("Slow response")
                .setBodyDelay(2, SECONDS)
        )

        val url = server.url("/slow").toString()

        // Short timeout should cause failure
        assertThatThrownBy {
            SimpleHttpClient.get(url, Duration.ofMillis(100))
        }.hasMessageContaining("timeout")
    }

    @Test
    fun `large files can be downloaded`() {
        val largeContent = "x".repeat(1024 * 100) // 100KB
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(largeContent)
        )

        val url = server.url("/large.dat").toString()
        val targetFile = tempDir.resolve("large.dat")

        SimpleHttpClient.downloadToPath(url, targetFile)

        assertThat(targetFile).exists()
        assertThat(Files.size(targetFile)).isEqualTo(largeContent.length.toLong())
    }
}
