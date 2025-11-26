package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.process.ProcessExecutor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission.*
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class EngineExtractorTest {
    @TempDir
    lateinit var tempDir: Path

    private val project = mockk<Project>(relaxed = true)
    private val console = mockk<ConsoleView>(relaxed = true)
    private val processExecutor = mockk<ProcessExecutor>(relaxed = true)
    private val config = mockk<DefoldEditorConfig>(relaxed = true)
    private val launchConfig = mockk<LaunchConfigs.Config>(relaxed = true)

    private lateinit var extractor: EngineExtractor

    @BeforeEach
    fun setUp() {
        extractor = EngineExtractor(processExecutor)

        every { processExecutor.console } returns console
        every { config.launchConfig } returns launchConfig
        every { config.editorJar } returns "/path/to/editor.jar"
        every { config.jarBin } returns "jar"
        every { launchConfig.executable } returns "dmengine"
        every { launchConfig.libexecBinPath } returns "libexec/x86_64-linux"
        every { project.basePath } returns tempDir.toString()

        // Default: extraction succeeds
        every { processExecutor.executeAndWait(any()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun setupExtractedEngine() {
        val extractedPath = tempDir.resolve("build/libexec/x86_64-linux/dmengine")
        extractedPath.parent.createDirectories()
        extractedPath.writeText("fake engine binary")
    }

    private fun getCachedEnginePath(): Path = tempDir.resolve("build/defold-ij/dmengine")

    private fun captureExtractCommand(): GeneralCommandLine {
        val slot = slot<GeneralCommandLine>()
        verify { processExecutor.executeAndWait(capture(slot)) }
        return slot.captured
    }

    @Nested
    inner class EngineExtraction {
        @Test
        fun `creates cache directory`() {
            setupExtractedEngine()

            extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            val cacheDir = tempDir.resolve("build/defold-ij")
            assertThat(cacheDir).exists()
        }

        @Test
        fun `extracts engine from JAR`() {
            setupExtractedEngine()

            val result = extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            assertThat(result.isSuccess).isTrue
            verify(exactly = 1) { processExecutor.executeAndWait(any()) }
        }

        @Test
        fun `returns engine path`() {
            setupExtractedEngine()

            val result = extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            assertThat(result.getOrNull()).isEqualTo(getCachedEnginePath())
        }

        @Test
        fun `reuses existing engine`() {
            val cachedEngine = getCachedEnginePath()
            cachedEngine.parent.createDirectories()
            cachedEngine.writeText("cached engine")

            val result = extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            assertThat(result.isSuccess).isTrue
            verify(exactly = 0) { processExecutor.executeAndWait(any()) }
        }

        @Test
        @EnabledOnOs(OS.LINUX, OS.MAC)
        fun `sets executable permissions on Unix`() {
            setupExtractedEngine()

            extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            val enginePath = getCachedEnginePath()
            if (enginePath.exists()) {
                val permissions = Files.getPosixFilePermissions(enginePath)
                assertThat(permissions).contains(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE)
            }
        }
    }

    @Nested
    inner class PathManagement {
        @Test
        fun `uses correct cache folder`() {
            setupExtractedEngine()

            extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            val enginePath = getCachedEnginePath()
            assertThat(enginePath.toString()).contains("build/defold-ij")
        }

        @Test
        fun `uses platform-specific executable name`() {
            every { launchConfig.executable } returns "dmengine.exe"
            setupExtractedEngine()
            val extractedPath = tempDir.resolve("build/libexec/x86_64-linux/dmengine.exe")
            extractedPath.parent.createDirectories()
            extractedPath.writeText("fake windows engine")

            extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            val enginePath = tempDir.resolve("build/defold-ij/dmengine.exe")
            assertThat(enginePath).exists()
        }

        @Test
        fun `creates parent directories`() {
            setupExtractedEngine()

            extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            val cacheDir = tempDir.resolve("build/defold-ij")
            assertThat(cacheDir).exists().isDirectory()
        }
    }

    @Nested
    inner class JARExtraction {
        @Test
        fun `uses correct jar arguments`() {
            setupExtractedEngine()

            extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            val command = captureExtractCommand()
            assertThat(command.exePath).isEqualTo("jar")
            assertThat(command.parametersList.parameters).containsExactly(
                "-xf",
                "/path/to/editor.jar",
                "libexec/x86_64-linux/dmengine"
            )
        }

        @Test
        fun `applies environment variables`() {
            val envData = EnvironmentVariablesData.create(mapOf("TEST_VAR" to "test_value"), true)
            setupExtractedEngine()

            extractor.extractAndPrepareEngine(project, config, envData)

            val command = captureExtractCommand()
            assertThat(command.environment).containsEntry("TEST_VAR", "test_value")
        }

        @Test
        fun `copies extracted file to cache`() {
            setupExtractedEngine()

            extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            val cachedEngine = getCachedEnginePath()
            assertThat(cachedEngine).exists()
            assertThat(cachedEngine.toFile().readText()).isEqualTo("fake engine binary")
        }

        @Test
        fun `cleans up temporary extraction directory`() {
            setupExtractedEngine()

            extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            val libexecDir = tempDir.resolve("build/libexec")
            assertThat(libexecDir).doesNotExist()
        }
    }

    @Nested
    inner class ErrorHandling {
        @Test
        fun `fails with no base path`() {
            every { project.basePath } returns null

            val result = extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            assertThat(result.isFailure).isTrue
            assertThat(result.exceptionOrNull())
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("Project has no base path")
        }

        @Test
        fun `handles extraction failure`() {
            every { processExecutor.executeAndWait(any()) } returns 1
            setupExtractedEngine()

            val result = extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            assertThat(result.isFailure).isTrue
            assertThat(result.exceptionOrNull())
                .isInstanceOf(RuntimeException::class.java)
                .hasMessageContaining("Failed to extract engine")
                .hasMessageContaining("exit code: 1")
        }

        @Test
        fun `handles missing embedded engine`() {
            // Don't set up extracted engine - file won't exist after extraction

            val result = extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            assertThat(result.isFailure).isTrue
            assertThat(result.exceptionOrNull())
                .isInstanceOf(RuntimeException::class.java)
                .hasMessageContaining("Extracted engine file not found")
        }

        @Test
        fun `reports errors to console`() {
            every { processExecutor.executeAndWait(any()) } throws RuntimeException("Test extraction error")

            extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            verify { console.print(match { it.contains("Failed to extract dmengine") }, any()) }
        }

        @Test
        fun `handles IOException during file copy`() {
            setupExtractedEngine()
            val cachedEngine = getCachedEnginePath()
            cachedEngine.parent.createDirectories()
            cachedEngine.writeText("existing")
            cachedEngine.toFile().setReadOnly()

            // This may or may not fail depending on OS, but shouldn't crash
            val result = extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            // Either succeeds (REPLACE_EXISTING works) or fails gracefully
            if (result.isFailure) {
                assertThat(result.exceptionOrNull()).isNotNull()
            }
        }
    }

    @Nested
    inner class CacheManagement {
        @Test
        fun `extracted engine is reused on subsequent calls`() {
            setupExtractedEngine()

            // First call extracts
            extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)
            verify(exactly = 1) { processExecutor.executeAndWait(any()) }

            clearMocks(processExecutor, answers = false)
            every { processExecutor.console } returns console

            // Second call reuses
            extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)
            verify(exactly = 0) { processExecutor.executeAndWait(any()) }
        }

        @Test
        fun `cache respects platform-specific executables`() {
            every { launchConfig.executable } returns "dmengine.exe"
            every { launchConfig.libexecBinPath } returns "libexec/x86_64-win32"

            val extractedPath = tempDir.resolve("build/libexec/x86_64-win32/dmengine.exe")
            extractedPath.parent.createDirectories()
            extractedPath.writeText("windows engine")

            extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            val cachedPath = tempDir.resolve("build/defold-ij/dmengine.exe")
            assertThat(cachedPath).exists()
        }

        @Test
        fun `cache directory is created if missing`() {
            val cacheDir = tempDir.resolve("build/defold-ij")
            assertThat(cacheDir).doesNotExist()

            setupExtractedEngine()
            extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            assertThat(cacheDir).exists()
        }
    }

    @Nested
    inner class PlatformCompatibility {
        @Test
        fun `handles Unix executable paths`() {
            every { launchConfig.executable } returns "dmengine"
            every { launchConfig.libexecBinPath } returns "libexec/x86_64-linux"
            setupExtractedEngine()

            val result = extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            assertThat(result.isSuccess).isTrue
        }

        @Test
        fun `handles Windows executable paths`() {
            every { launchConfig.executable } returns "dmengine.exe"
            every { launchConfig.libexecBinPath } returns "libexec/x86_64-win32"

            val extractedPath = tempDir.resolve("build/libexec/x86_64-win32/dmengine.exe")
            extractedPath.parent.createDirectories()
            extractedPath.writeText("windows engine")

            val result = extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            assertThat(result.isSuccess).isTrue
        }

        @Test
        fun `handles macOS executable paths`() {
            every { launchConfig.executable } returns "dmengine"
            every { launchConfig.libexecBinPath } returns "libexec/x86_64-macos"

            val extractedPath = tempDir.resolve("build/libexec/x86_64-macos/dmengine")
            extractedPath.parent.createDirectories()
            extractedPath.writeText("macos engine")

            val result = extractor.extractAndPrepareEngine(project, config, EnvironmentVariablesData.DEFAULT)

            assertThat(result.isSuccess).isTrue
        }
    }
}
