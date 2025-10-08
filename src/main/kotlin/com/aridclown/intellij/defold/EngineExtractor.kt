package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.BUILD_CACHE_FOLDER
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.aridclown.intellij.defold.util.trySilently
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.PosixFilePermission.*
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * Handles extraction and preparation of the Defold engine executable.
 *
 * Stores extracted engine in build/[BUILD_CACHE_FOLDER]
 */
class EngineExtractor(
    private val console: ConsoleView,
    private val processExecutor: ProcessExecutor
) {

    fun extractAndPrepareEngine(
        project: Project,
        config: DefoldEditorConfig,
        envData: EnvironmentVariablesData
    ): Result<Path> = runCatching {
        val workspace = project.basePath?.let(Path::of)
            ?: throw IllegalStateException("Project has no base path")

        createEnginePath(workspace, config)
            .extractEngineFromJar(config, workspace, envData)
    }

    private fun createEnginePath(workspace: Path, config: DefoldEditorConfig): Path {
        val launcherDir = workspace
            .resolve("build")
            .resolve(BUILD_CACHE_FOLDER)
            .also(Files::createDirectories)

        return launcherDir.resolve(config.launchConfig.executable)
    }

    private fun Path.extractEngineFromJar(
        config: DefoldEditorConfig,
        workspace: Path,
        envData: EnvironmentVariablesData
    ) = apply {
        if (exists()) return@apply // already extracted

        val buildDir = workspace.resolve("build")
        val internalExec = "${config.launchConfig.libexecBinPath}/${config.launchConfig.executable}"

        val extractCommand = GeneralCommandLine(config.jarBin, "-xf", config.editorJar, internalExec)
            .withWorkingDirectory(buildDir)
            .applyEnvironment(envData)

        try {
            val exitCode = processExecutor.executeAndWait(extractCommand)
            if (exitCode != 0) {
                throw RuntimeException("Failed to extract engine (exit code: $exitCode)")
            }

            createEngineFiles(buildDir, internalExec, this)
        } catch (e: Exception) {
            console.printError("Failed to extract dmengine: ${e.message}")
            throw e
        }
    }

    private fun createEngineFiles(buildDir: Path, internalExec: String, enginePath: Path) {
        val extractedFile = buildDir.resolve(internalExec)
        if (Files.exists(extractedFile)) {
            enginePath.parent?.let(Files::createDirectories)
            Files.copy(extractedFile, enginePath, REPLACE_EXISTING)
            makeExecutable(enginePath)

            // clean up tmp directory
            buildDir.resolve("libexec")
                .takeIf(Files::exists)
                ?.toFile()
                ?.deleteRecursively()
        } else {
            throw RuntimeException("Extracted engine file not found at: ${extractedFile.toAbsolutePath().pathString}")
        }
    }

    private fun makeExecutable(file: Path) = trySilently { // Ignore on non-POSIX systems
        val permissions = setOf(
            OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ
        )
        Files.setPosixFilePermissions(file, permissions)
    }
}
