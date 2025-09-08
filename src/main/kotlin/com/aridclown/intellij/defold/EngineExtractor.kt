package com.aridclown.intellij.defold

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission.*

/**
 * Handles extraction and preparation of the Defold engine executable
 */
class EngineExtractor(
    private val console: ConsoleView,
    private val processExecutor: ProcessExecutor
) {

    fun extractAndPrepareEngine(
        project: Project,
        config: DefoldEditorConfig
    ): Result<File> = runCatching {
        val workspace = project.basePath
            ?: throw IllegalStateException("Project has no base path")

        val enginePath = createEngineDirectory(workspace, config)

        if (!enginePath.exists()) {
            extractEngineFromJar(config, workspace, enginePath)
        }

        enginePath
    }

    private fun createEngineDirectory(workspace: String, config: DefoldEditorConfig): File {
        val buildDir = File(workspace, "build")
        val launcherDir = File(buildDir, "defoldkit").also { it.mkdirs() }

        return File(launcherDir, config.launchConfig.executable)
    }

    private fun extractEngineFromJar(
        config: DefoldEditorConfig,
        workspace: String,
        enginePath: File
    ) {
        val buildDir = File(workspace, "build")
        val internalPath = "${config.launchConfig.libexecBinPath}/${config.launchConfig.executable}"

        // Extract engine from the jar
        val extractCommand = GeneralCommandLine(config.jarBin, "-xf", config.editorJar, internalPath)
            .withWorkingDirectory(Paths.get(buildDir.path))

        try {
            val exitCode = processExecutor.executeAndWait(extractCommand)
            if (exitCode != 0) {
                throw RuntimeException("Failed to extract engine (exit code: $exitCode)")
            }

            val extractedFile = File(buildDir, internalPath)
            if (extractedFile.exists()) {
                extractedFile.copyTo(enginePath, overwrite = true)
                makeExecutable(enginePath)
            } else {
                throw RuntimeException("Extracted engine file not found at: ${extractedFile.absolutePath}")
            }
        } catch (e: Exception) {
            console.print("Failed to extract dmengine: ${e.message}\n", ERROR_OUTPUT)
            throw e
        }
    }

    private fun makeExecutable(file: File) = try {
        val permissions = setOf(
            OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ
        )
        Files.setPosixFilePermissions(file.toPath(), permissions)
    } catch (_: Exception) {
        // Ignore on non-POSIX systems
    }
}
