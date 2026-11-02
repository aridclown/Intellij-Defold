package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.PLUGIN_DIRECTORY_NAME
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.UnknownHostException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    private lateinit var systemPathDir: Path

    private val annotationsCacheDir: Path
        get() = systemPathDir.resolve(PLUGIN_DIRECTORY_NAME).resolve("std")

    @BeforeEach
    fun setUp() {
        project = projectFixture.get()
        clearNotifications()

        downloader = mockk(relaxed = true)
        every { downloader.downloadAndExtract(any(), any()) } answers {
            val api = secondArg<Path>().resolve("defold_api/api.lua")
            Files.createDirectories(api.parent)
            Files.writeString(api, "-- downloaded")
        }
        luarcManager = mockk(relaxed = true)

        mockkStatic(PathManager::class)
        mockkObject(DefoldProjectService.Companion)
        every { PathManager.getSystemPath() } returns systemPathDir.toString()
        every { project.defoldVersion } returns "1.6.5"

        manager = DefoldAnnotationsManager(project, downloader, luarcManager, editorJar = { null })
    }

    @AfterEach
    fun tearDown() {
        clearNotifications()
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `uses editor annotations without contacting GitHub`(): Unit = timeoutRunBlocking {
        val bundled = mockk<BundledAnnotations>()
        val jar = systemPathDir.resolve("editor.jar")
        val cache = cacheDirFor("1.6.5")
        every { bundled.extract(jar, any()) } answers {
            val api = secondArg<Path>().resolve("defold_api/go.lua")
            Files.createDirectories(api.parent)
            Files.writeString(api, "---@meta\ngo = {}")
            true
        }
        manager = DefoldAnnotationsManager(project, downloader, luarcManager, bundled, { jar })

        manager.ensureAnnotationsAttached()

        assertThat(cache.resolve("defold_api/go.lua")).exists()
        verify(exactly = 0) { downloader.resolveDownloadUrl(any()) }
        verify { luarcManager.ensureConfiguration(project, cache.apiDir()) }
    }

    @Test
    fun `reload replaces old annotations with the editor bundle`(): Unit = timeoutRunBlocking {
        val bundled = mockk<BundledAnnotations>()
        val jar = systemPathDir.resolve("editor.jar")
        val cache = cacheDirFor("1.6.5")
        Files.createDirectories(cache.apiDir())
        Files.writeString(cache.apiDir().resolve("old.lua"), "-- old")
        every { bundled.extract(jar, any()) } answers {
            val api = secondArg<Path>().resolve("defold_api/go.lua")
            Files.createDirectories(api.parent)
            Files.writeString(api, "---@meta\ngo = {}")
            true
        }
        manager = DefoldAnnotationsManager(project, downloader, luarcManager, bundled, { jar })

        manager.reloadAnnotations()

        assertThat(cache.apiDir().resolve("go.lua")).exists()
        assertThat(cache.apiDir().resolve("old.lua")).doesNotExist()
        verify(exactly = 0) { downloader.resolveDownloadUrl(any()) }
    }

    @Test
    fun `falls back to GitHub when editor has no bundled annotations`(): Unit = timeoutRunBlocking {
        val bundled = mockk<BundledAnnotations>()
        val jar = systemPathDir.resolve("editor.jar")
        every { bundled.extract(jar, any()) } returns false
        manager = DefoldAnnotationsManager(project, downloader, luarcManager, bundled, { jar })

        manager.ensureAnnotationsAttached()

        verify { downloader.resolveDownloadUrl("1.6.5") }
    }

    @Test
    fun `unreadable editor falls back without retaining partial bundle files`(): Unit = timeoutRunBlocking {
        val bundled = mockk<BundledAnnotations>()
        every { bundled.extract(any(), any()) } answers {
            val partial = secondArg<Path>().resolve("defold_api/partial.lua")
            Files.createDirectories(partial.parent)
            Files.writeString(partial, "-- incomplete")
            throw IOException("corrupt jar")
        }
        manager = DefoldAnnotationsManager(project, downloader, luarcManager, bundled, { systemPathDir.resolve("missing.jar") })
        manager.ensureAnnotationsAttached()
        val cache = cacheDirFor("1.6.5")
        assertThat(cache.apiDir().resolve("api.lua")).exists()
        assertThat(cache.apiDir().resolve("partial.lua")).doesNotExist()
    }

    @Test
    fun `existing downloaded cache migrates to bundled annotations once`(): Unit = timeoutRunBlocking {
        val cache = cacheDirFor("1.6.5")
        Files.createDirectories(cache.apiDir())
        Files.writeString(cache.apiDir().resolve("old.lua"), "-- old")
        val bundled = mockk<BundledAnnotations>()
        every { bundled.extract(any(), any()) } answers {
            val api = secondArg<Path>().resolve("defold_api/go.lua")
            Files.createDirectories(api.parent)
            Files.writeString(api, "---@meta")
            true
        }
        manager = DefoldAnnotationsManager(project, downloader, luarcManager, bundled, { systemPathDir.resolve("editor.jar") })
        manager.ensureAnnotationsAttached()
        manager.ensureAnnotationsAttached()
        assertThat(cache.apiDir().resolve("go.lua")).exists()
        assertThat(cache.apiDir().resolve("old.lua")).doesNotExist()
        verify(exactly = 1) { bundled.extract(any(), any()) }
        verify(exactly = 0) { downloader.resolveDownloadUrl(any()) }
    }

    @Test
    fun `failed bundle migration preserves working downloaded annotations`(): Unit = timeoutRunBlocking {
        val cache = cacheDirFor("1.6.5")
        Files.createDirectories(cache.apiDir())
        Files.writeString(cache.apiDir().resolve("old.lua"), "-- working")
        val bundled = mockk<BundledAnnotations>()
        every { bundled.extract(any(), any()) } throws IOException("cannot read editor")
        manager = DefoldAnnotationsManager(project, downloader, luarcManager, bundled, { systemPathDir.resolve("editor.jar") })

        manager.ensureAnnotationsAttached()

        assertThat(Files.readString(cache.apiDir().resolve("old.lua"))).isEqualTo("-- working")
        assertThat(cache.resolve("bundled")).doesNotExist()
        verify(exactly = 0) { downloader.resolveDownloadUrl(any()) }
    }

    @Test
    fun `failed initial download leaves no partial cache and retries`(): Unit = timeoutRunBlocking {
        every { downloader.downloadAndExtract(any(), any()) } answers {
            val api = secondArg<Path>().resolve("defold_api/partial.lua")
            Files.createDirectories(api.parent)
            Files.writeString(api, "-- partial")
            throw IOException("interrupted")
        }
        manager.ensureAnnotationsAttached()
        manager.ensureAnnotationsAttached()
        assertThat(cacheDirFor("1.6.5").apiDir()).doesNotExist()
        verify(exactly = 2) { downloader.resolveDownloadUrl(any()) }
    }

    @Test
    fun `overlapping setup cannot recreate a cache during replacement`(): Unit = timeoutRunBlocking {
        val cache = cacheDirFor("1.6.5")
        Files.createDirectories(cache.apiDir())
        Files.writeString(cache.apiDir().resolve("old.lua"), "-- working")
        val backedUp = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        manager = DefoldAnnotationsManager(project, downloader, luarcManager, editorJar = { null }, moveCache = { source, target ->
            Files.move(source, target)
            if (source == cache) {
                backedUp.countDown()
                check(proceed.await(10, TimeUnit.SECONDS))
            }
        })
        val reload = async(Dispatchers.IO) { manager.reloadAnnotations() }
        try {
            assertThat(withContext(Dispatchers.IO) { backedUp.await(10, TimeUnit.SECONDS) }).isTrue()
            val other = DefoldAnnotationsManager(project, downloader, luarcManager, editorJar = { null })
            val setup = async(start = CoroutineStart.UNDISPATCHED) { other.ensureAnnotationsAttached() }
            assertThat(cache).doesNotExist()
            proceed.countDown()
            reload.await()
            setup.await()
            assertThat(cache.apiDir().resolve("api.lua")).exists()
        } finally {
            proceed.countDown()
        }
    }

    @Test
    fun `replacement failure restores the previous working cache`(): Unit = timeoutRunBlocking {
        val cache = cacheDirFor("1.6.5")
        Files.createDirectories(cache.apiDir())
        Files.writeString(cache.apiDir().resolve("old.lua"), "-- working")
        var moves = 0
        manager = DefoldAnnotationsManager(project, downloader, luarcManager, editorJar = { null }, moveCache = { source, target ->
            moves++
            if (moves == 2) throw IOException("replacement failed")
            Files.move(source, target)
        })

        manager.reloadAnnotations()

        assertThat(Files.readString(cache.apiDir().resolve("old.lua"))).isEqualTo("-- working")
        assertThat(moves).isEqualTo(3)
    }

    @Test
    fun `empty successful download cannot replace working annotations`(): Unit = timeoutRunBlocking {
        val cache = cacheDirFor("1.6.5")
        Files.createDirectories(cache.apiDir())
        Files.writeString(cache.apiDir().resolve("old.lua"), "-- working")
        every { downloader.downloadAndExtract(any(), any()) } just Runs

        manager.reloadAnnotations()

        assertThat(Files.readString(cache.apiDir().resolve("old.lua"))).isEqualTo("-- working")
        expectNotification("Defold annotations failed", WARNING)
    }

    @Test
    fun `downloads and extracts annotations when cache is empty`(): Unit = timeoutRunBlocking {
        val downloadUrl = "https://example.com/annotations.zip"
        val cacheDir = runEnsure("1.6.5") { dir ->
            every { downloader.resolveDownloadUrl("1.6.5") } returns downloadUrl
        }

        verify { downloader.resolveDownloadUrl("1.6.5") }
        verify { downloader.downloadAndExtract(downloadUrl, any()) }
        verify { luarcManager.ensureConfiguration(project, cacheDir.apiDir()) }
    }

    @Test
    fun `uses latest when version is null`(): Unit = timeoutRunBlocking {
        val downloadUrl = "https://example.com/latest.zip"
        val cacheDir = runEnsure(null) { dir ->
            every { downloader.resolveDownloadUrl(null) } returns downloadUrl
        }

        verify { downloader.resolveDownloadUrl(null) }
        verify { downloader.downloadAndExtract(downloadUrl, any()) }
    }

    @Test
    fun `uses latest when version is blank`(): Unit = timeoutRunBlocking {
        val cacheDir = runEnsure("  ") { dir ->
            val downloadUrl = "https://example.com/latest.zip"
            every { downloader.resolveDownloadUrl("  ") } returns downloadUrl
        }

        assertThat(Files.exists(cacheDir)).isTrue
    }

    @Test
    fun `skips download when annotations already exist`(): Unit = timeoutRunBlocking {
        val cacheDir = runEnsure("1.6.5") { dir ->
            Files.createDirectories(dir.apiDir())
            Files.createFile(dir.apiDir().resolve("api.lua"))
        }

        // Should not attempt download
        verify(exactly = 0) { downloader.resolveDownloadUrl(any()) }
        verify(exactly = 0) { downloader.downloadAndExtract(any(), any()) }

        // But should still ensure configuration
        verify { luarcManager.ensureConfiguration(project, cacheDir.apiDir()) }
    }

    @Test
    fun `downloads when cache has files but no annotations directory`(): Unit = timeoutRunBlocking {
        val downloadUrl = "https://example.com/annotations.zip"
        val cacheDir = runEnsure("1.6.5") { dir ->
            Files.createDirectories(dir)
            Files.createFile(dir.resolve("unrelated.txt"))
            every { downloader.resolveDownloadUrl("1.6.5") } returns downloadUrl
        }

        verify { downloader.downloadAndExtract(downloadUrl, any()) }
    }

    @Test
    fun `repairs annotations cached under the legacy tmp directory`(): Unit = timeoutRunBlocking {
        val cacheDir = runEnsure("1.6.5") { dir ->
            val legacyApiFile = dir.resolve("tmp/defold_api/api.lua")
            Files.createDirectories(legacyApiFile.parent)
            Files.writeString(legacyApiFile, "-- cached")
        }

        assertThat(Files.readString(cacheDir.apiDir().resolve("api.lua"))).isEqualTo("-- cached")
        assertThat(cacheDir.resolve("tmp")).doesNotExist()
        verify(exactly = 0) { downloader.resolveDownloadUrl(any()) }
        verify(exactly = 0) { downloader.downloadAndExtract(any(), any()) }
        verify { luarcManager.ensureConfiguration(project, cacheDir.apiDir()) }
    }

    @Test
    fun `removes stale legacy cache when valid annotations exist`(): Unit = timeoutRunBlocking {
        val cacheDir = runEnsure("1.6.5") { dir ->
            val apiFile = dir.apiDir().resolve("api.lua")
            val legacyApiFile = dir.resolve("tmp/defold_api/old.lua")
            Files.createDirectories(apiFile.parent)
            Files.createDirectories(legacyApiFile.parent)
            Files.writeString(apiFile, "-- current")
            Files.writeString(legacyApiFile, "-- stale")
        }

        assertThat(Files.readString(cacheDir.apiDir().resolve("api.lua"))).isEqualTo("-- current")
        assertThat(cacheDir.resolve("tmp")).doesNotExist()
        verify(exactly = 0) { downloader.downloadAndExtract(any(), any()) }
    }

    @Test
    fun `handles UnknownHostException gracefully`(): Unit = timeoutRunBlocking {
        val cacheDir = runEnsure("1.6.5") {
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
        val cacheDir = runEnsure("1.6.5") { dir ->
            every { downloader.resolveDownloadUrl("1.6.5") } returns downloadUrl
            every { downloader.downloadAndExtract(downloadUrl, any()) } throws IllegalStateException("boom")
        }

        verify { luarcManager.ensureConfiguration(project, cacheDir.apiDir()) }

        expectNotification("Defold annotations failed", WARNING) { content ->
            assertThat(content).contains("boom")
        }
    }

    @Test
    fun `creates cache directory structure`(): Unit = timeoutRunBlocking {
        val downloadUrl = "https://example.com/annotations.zip"
        val cacheDir = runEnsure("1.6.5") { dir ->
            every { downloader.resolveDownloadUrl("1.6.5") } returns downloadUrl
        }

        assertThat(Files.exists(cacheDir)).isTrue
        assertThat(Files.isDirectory(cacheDir)).isTrue
    }

    @Test
    fun `downloads when cache directory is empty`(): Unit = timeoutRunBlocking {
        val downloadUrl = "https://example.com/annotations.zip"
        val cacheDir = runEnsure("1.6.5") { dir ->
            Files.createDirectories(dir)
            every { downloader.resolveDownloadUrl("1.6.5") } returns downloadUrl
        }

        verify { downloader.downloadAndExtract(downloadUrl, any()) }
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

    @Test
    fun `reload replaces annotations only after successful download`(): Unit = timeoutRunBlocking {
        val downloadUrl = "https://example.com/reload.zip"
        val cacheDir = runReload("1.6.5") { dir ->
            val apiDir = dir.apiDir()
            Files.createDirectories(apiDir)
            Files.writeString(apiDir.resolve("api.lua"), "-- old content")

            every { downloader.resolveDownloadUrl("1.6.5") } returns downloadUrl
            every { downloader.downloadAndExtract(downloadUrl, any()) } answers {
                val destination = secondArg<Path>()
                val newApi = destination.resolve("defold_api/api.lua")
                Files.createDirectories(newApi.parent)
                Files.writeString(newApi, "-- new content")
            }
        }

        assertThat(Files.readString(cacheDir.apiDir().resolve("api.lua"))).isEqualTo("-- new content")
        verify { luarcManager.ensureConfiguration(project, cacheDir.apiDir()) }
    }

    @Test
    fun `reload keeps existing annotations when download fails`(): Unit = timeoutRunBlocking {
        val downloadUrl = "https://example.com/reload.zip"
        val cacheDir = runReload("1.6.5") { dir ->
            val apiDir = dir.apiDir()
            Files.createDirectories(apiDir)
            Files.writeString(apiDir.resolve("api.lua"), "-- cached")

            every { downloader.resolveDownloadUrl("1.6.5") } returns downloadUrl
            every { downloader.downloadAndExtract(downloadUrl, any()) } throws IllegalStateException("reload failed")
        }

        assertThat(Files.readString(cacheDir.apiDir().resolve("api.lua"))).isEqualTo("-- cached")

        expectNotification("Defold annotations failed", WARNING) { content ->
            assertThat(content).contains("reload failed")
        }
        verify { luarcManager.ensureConfiguration(project, cacheDir.apiDir()) }
    }

    private suspend fun runEnsure(
        version: String?,
        setup: (Path) -> Unit = {}
    ): Path = cacheDirFor(version).apply {
        every { project.defoldVersion } returns version
        setup(this)
        manager.ensureAnnotationsAttached()
    }

    private suspend fun runReload(
        version: String?,
        setup: (Path) -> Unit = {}
    ): Path = cacheDirFor(version).apply {
        every { project.defoldVersion } returns version
        setup(this)
        manager.reloadAnnotations()
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
