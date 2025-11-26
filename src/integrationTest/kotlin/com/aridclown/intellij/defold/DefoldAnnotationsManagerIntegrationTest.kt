package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldVersion
import com.aridclown.intellij.defold.util.stdLibraryRootPath
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.WARNING
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.net.UnknownHostException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
@TestFixtures
class DefoldAnnotationsManagerIntegrationTest {
    private val tempPathFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempPathFixture)

    private lateinit var project: Project
    private lateinit var downloader: AnnotationsDownloader
    private lateinit var luarcManager: LuarcConfigurationManager
    private lateinit var manager: DefoldAnnotationsManager

    @TempDir
    private lateinit var annotationsCacheDir: Path

    @BeforeEach
    fun setUp() {
        project = projectFixture.get()
        clearNotifications()

        downloader = mockk(relaxed = true)
        luarcManager = mockk(relaxed = true)

        mockkStatic(PathManager::class)
        mockkObject(DefoldProjectService.Companion)
        every { PathManager.getPluginsDir() } returns annotationsCacheDir
        every { project.defoldVersion } returns "1.6.5"

        manager = DefoldAnnotationsManager(project, downloader, luarcManager)
    }

    @AfterEach
    fun tearDown() {
        clearNotifications()
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `downloads and extracts annotations when cache is empty`(): Unit = timeoutRunBlocking {
        val downloadUrl = "https://example.com/annotations.zip"
        val cacheDir =
            runEnsure("1.6.5") { dir ->
                every { downloader.resolveDownloadUrl("1.6.5") } returns downloadUrl
                every { downloader.downloadAndExtract(downloadUrl, dir) } just Runs
            }

        verify { downloader.resolveDownloadUrl("1.6.5") }
        verify { downloader.downloadAndExtract(downloadUrl, cacheDir) }
        verify { luarcManager.ensureConfiguration(project, cacheDir.apiDir()) }
    }

    @Test
    fun `uses latest when version is null`(): Unit = timeoutRunBlocking {
        val downloadUrl = "https://example.com/latest.zip"
        val cacheDir =
            runEnsure(null) { dir ->
                every { downloader.resolveDownloadUrl(null) } returns downloadUrl
                every { downloader.downloadAndExtract(downloadUrl, dir) } just Runs
            }

        verify { downloader.resolveDownloadUrl(null) }
        verify { downloader.downloadAndExtract(downloadUrl, cacheDir) }
    }

    @Test
    fun `uses latest when version is blank`(): Unit = timeoutRunBlocking {
        val cacheDir =
            runEnsure("  ") { dir ->
                val downloadUrl = "https://example.com/latest.zip"
                every { downloader.resolveDownloadUrl("  ") } returns downloadUrl
                every { downloader.downloadAndExtract(downloadUrl, dir) } just Runs
            }

        assertThat(Files.exists(cacheDir)).isTrue
    }

    @Test
    fun `skips download when annotations already exist`(): Unit = timeoutRunBlocking {
        val cacheDir =
            runEnsure("1.6.5") { dir ->
                Files.createDirectories(dir)
                Files.createFile(dir.resolve("existing_file.txt"))
            }

        // Should not attempt download
        verify(exactly = 0) { downloader.resolveDownloadUrl(any()) }
        verify(exactly = 0) { downloader.downloadAndExtract(any(), any()) }

        // But should still ensure configuration
        verify { luarcManager.ensureConfiguration(project, cacheDir.apiDir()) }
    }

    @Test
    fun `handles UnknownHostException gracefully`(): Unit = timeoutRunBlocking {
        val cacheDir =
            runEnsure("1.6.5") {
                every { downloader.resolveDownloadUrl("1.6.5") } throws UnknownHostException("api.github.com")
            }

        // Should still ensure configuration despite error
        verify { luarcManager.ensureConfiguration(project, cacheDir.apiDir()) }

        val notification =
            expectNotification(
                title = "Defold annotations failed",
                type = WARNING
            ) { content ->
                assertThat(content).contains(
                    "Failed to download Defold annotations. Verify your connection, proxy, and firewall settings before trying again."
                )
            }
        assertThat(notification.actions).isNotEmpty
    }

    @Test
    fun `handles unexpected errors with warning notification`(): Unit = timeoutRunBlocking {
        val downloadUrl = "https://example.com/annotations.zip"
        val cacheDir =
            runEnsure("1.6.5") { dir ->
                every { downloader.resolveDownloadUrl("1.6.5") } returns downloadUrl
                every { downloader.downloadAndExtract(downloadUrl, dir) } throws IllegalStateException("boom")
            }

        verify { luarcManager.ensureConfiguration(project, cacheDir.apiDir()) }

        expectNotification("Defold annotations failed", WARNING) { content ->
            assertThat(content).contains("boom")
        }
    }

    @Test
    fun `creates cache directory structure`(): Unit = timeoutRunBlocking {
        val downloadUrl = "https://example.com/annotations.zip"
        val cacheDir =
            runEnsure("1.6.5") { dir ->
                every { downloader.resolveDownloadUrl("1.6.5") } returns downloadUrl
                every { downloader.downloadAndExtract(downloadUrl, dir) } just Runs
            }

        assertThat(Files.exists(cacheDir)).isTrue
        assertThat(Files.isDirectory(cacheDir)).isTrue
    }

    @Test
    fun `downloads when cache directory is empty`(): Unit = timeoutRunBlocking {
        val downloadUrl = "https://example.com/annotations.zip"
        val cacheDir =
            runEnsure("1.6.5") { dir ->
                Files.createDirectories(dir)
                every { downloader.resolveDownloadUrl("1.6.5") } returns downloadUrl
                every { downloader.downloadAndExtract(downloadUrl, dir) } just Runs
            }

        verify { downloader.downloadAndExtract(downloadUrl, cacheDir) }
    }

    @Test
    fun `handles when cache path is a file instead of directory`(): Unit = timeoutRunBlocking {
        val cachePath = cacheDirFor("1.6.5")
        Files.createDirectories(cachePath.parent)
        Files.createFile(cachePath)

        assertThrows<FileAlreadyExistsException> {
            manager.ensureAnnotationsAttached()
        }
    }

    private suspend fun runEnsure(
        version: String?,
        setup: (Path) -> Unit = {}
    ): Path = cacheDirFor(version).apply {
        every { project.defoldVersion } returns version
        setup(this)
        manager.ensureAnnotationsAttached()
    }

    private fun cacheDirFor(version: String?): Path = stdLibraryRootPath()
        .resolve(version.takeUnless { it.isNullOrBlank() } ?: "latest")

    private fun Path.apiDir(): Path = resolve("defold_api")

    private fun expectNotification(
        title: String,
        type: NotificationType,
        contentAssertion: (String) -> Unit = {}
    ): Notification = notificationByTitle(title)!!.apply {
        assertThat(this.type).isEqualTo(type)
        contentAssertion(this.content)
    }

    private fun clearNotifications() = allNotifications().forEach(Notification::expire)

    private fun notificationByTitle(title: String): Notification? = allNotifications().lastOrNull { it.title == title }

    private fun allNotifications(): List<Notification> = NotificationsManager
        .getNotificationsManager()
        .getNotificationsOfType(Notification::class.java, project)
        .toList()
}
