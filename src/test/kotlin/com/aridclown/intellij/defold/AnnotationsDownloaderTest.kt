package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.util.SimpleHttpClient
import com.aridclown.intellij.defold.util.SimpleHttpClient.SimpleHttpResponse
import com.intellij.testFramework.TestLoggerFactory.TestLoggerAssertionError
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AnnotationsDownloaderTest {
    private lateinit var downloader: AnnotationsDownloader

    @TempDir
    private lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        mockkObject(SimpleHttpClient)
        downloader = AnnotationsDownloader()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(SimpleHttpClient)
    }

    @ParameterizedTest
    @CsvSource("' '", "null", nullValues = ["null"])
    fun `resolves latest release URL when version is invalid`(defoldVersion: String?) {
        val releaseJson =
            """
            {
                "tag_name": "1.5.0",
                "assets": [
                    {
                        "name": "defold-annotations.zip",
                        "browser_download_url": "https://github.com/download/annotations.zip"
                    }
                ]
            }
            """.trimIndent()

        every { SimpleHttpClient.get(any(), any()) } returns SimpleHttpResponse(200, releaseJson)

        val url = downloader.resolveDownloadUrl(defoldVersion)

        assertThat(url).isEqualTo("https://github.com/download/annotations.zip")
        verify {
            SimpleHttpClient.get(
                "https://api.github.com/repos/astrochili/defold-annotations/releases/latest",
                Duration.ofSeconds(10)
            )
        }
    }

    @Test
    fun `throws exception when no assets found in release`() {
        val releaseJson =
            """
            {
                "tag_name": "1.0.0",
                "assets": []
            }
            """.trimIndent()

        every { SimpleHttpClient.get(any(), any()) } returns SimpleHttpResponse(200, releaseJson)

        // IntelliJ test framework treats logger.error as test failure,
        // so we expect TestLoggerAssertionError to be thrown
        val exception =
            assertThrows<TestLoggerAssertionError> {
                downloader.resolveDownloadUrl("1.0.0")
            }

        assertThat(exception.cause?.message).contains("No assets found in release")
    }

    @Test
    fun `propagates UnknownHostException when network unavailable`() {
        every { SimpleHttpClient.get(any(), any()) } throws UnknownHostException("api.github.com")

        assertThrows<UnknownHostException> {
            downloader.resolveDownloadUrl("1.0.0")
        }
    }

    @Test
    fun `wraps InterruptedIOException with timeout message`() {
        every { SimpleHttpClient.get(any(), any()) } throws InterruptedIOException("timeout")

        // InterruptedIOException gets wrapped but thrown directly (not logged)
        val exception =
            assertThrows<Exception> {
                downloader.resolveDownloadUrl("1.0.0")
            }

        assertThat(exception.message).isEqualTo("Could not resolve Defold annotations due to timeout")
        assertThat(exception.cause).isInstanceOf(InterruptedIOException::class.java)
    }

    @Test
    fun `informs when requested release is missing`() {
        val errorJson =
            """
            {
                "message": "Not Found",
                "documentation_url": "https://docs.github.com/rest/releases/releases#get-a-release-by-tag-name",
                "status": "404"
            }
            """.trimIndent()

        every { SimpleHttpClient.get(any(), any()) } returns SimpleHttpResponse(404, errorJson)

        val exception = assertThrows<Exception> {
            downloader.resolveDownloadUrl("1.2.3")
        }

        assertThat(exception.message)
            .contains("release '1.2.3' was not found")
    }

    @Test
    fun `wraps generic exceptions with download URL error message`() {
        every { SimpleHttpClient.get(any(), any()) } throws RuntimeException("Something went wrong")

        // Generic exceptions get logged as error
        val exception =
            assertThrows<TestLoggerAssertionError> {
                downloader.resolveDownloadUrl("1.0.0")
            }

        // The logger message is "Failed to fetch Defold annotations release asset url"
        assertThat(exception.message).contains("Failed to fetch Defold annotations release asset url")
    }

    @Test
    fun `downloads and extracts zip file to target directory`() {
        val zipFile = createTestZipFile()
        val targetDir = tempDir.resolve("output")

        every { SimpleHttpClient.downloadToPath(any(), any()) } answers {
            val destPath = secondArg<Path>()
            Files.copy(zipFile, destPath, REPLACE_EXISTING)
        }

        downloader.downloadAndExtract("https://example.com/annotations.zip", targetDir)

        assertThat(Files.exists(targetDir.resolve("defold_api/api.lua"))).isTrue
        assertThat(Files.readString(targetDir.resolve("defold_api/api.lua"))).isEqualTo("-- API content")
        assertThat(Files.exists(targetDir.resolve("defold_api/subdir/nested.lua"))).isTrue
    }

    @Test
    fun `cleans up temporary zip file after extraction`() {
        val zipFile = createTestZipFile()
        val targetDir = tempDir.resolve("output")
        val capturedTempFiles = mutableListOf<Path>()

        every { SimpleHttpClient.downloadToPath(any(), any()) } answers {
            val destPath = secondArg<Path>()
            capturedTempFiles.add(destPath)
            Files.copy(zipFile, destPath, REPLACE_EXISTING)
        }

        downloader.downloadAndExtract("https://example.com/annotations.zip", targetDir)

        // Temp file should be deleted
        capturedTempFiles.forEach { tempFile ->
            assertThat(Files.exists(tempFile)).isFalse
        }
    }

    private fun createTestZipFile(): Path {
        val zipPath = tempDir.resolve("test.zip")

        ZipOutputStream(Files.newOutputStream(zipPath)).use { zos ->
            // Add a file
            zos.putNextEntry(ZipEntry("defold_api/api.lua"))
            zos.write("-- API content".toByteArray())
            zos.closeEntry()

            // Add a directory
            zos.putNextEntry(ZipEntry("defold_api/subdir/"))
            zos.closeEntry()

            // Add a nested file
            zos.putNextEntry(ZipEntry("defold_api/subdir/nested.lua"))
            zos.write("-- Nested content".toByteArray())
            zos.closeEntry()
        }

        return zipPath
    }
}
