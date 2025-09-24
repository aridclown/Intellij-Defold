package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.process.ProcessExecutor
import com.aridclown.intellij.defold.util.trySilently
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission.*
import kotlin.io.path.Path

/**
 * Handles extraction and preparation of the Defold engine executable.
 *
 * Reuses "Defold Kit (vscode)" path: `build/defoldkit/dmengine`
 */
class EngineExtractor(
    private val console: ConsoleView,
    private val processExecutor: ProcessExecutor
) {

    fun extractAndPrepareEngine(
        project: Project,
        config: DefoldEditorConfig,
        envData: EnvironmentVariablesData
    ): Result<File> = runCatching {
        val workspace = project.basePath
            ?: throw IllegalStateException("Project has no base path")

        createEngineDirectory(workspace, config)
            .extractEngineFromJar(config, workspace, envData)
    }

    private fun createEngineDirectory(workspace: String, config: DefoldEditorConfig): File {
        val buildDir = File(workspace, "build")
        val launcherDir = File(buildDir, "defoldkit")
            .also(File::mkdirs)

        return File(launcherDir, config.launchConfig.executable)
    }

    private fun File.extractEngineFromJar(
        config: DefoldEditorConfig,
        workspace: String,
        envData: EnvironmentVariablesData
    ) = apply {
        if (exists()) return@apply // already extracted

        val buildDir = File(workspace, "build")
        val internalExec = "${config.launchConfig.libexecBinPath}/${config.launchConfig.executable}"

        val extractCommand = GeneralCommandLine(config.jarBin, "-xf", config.editorJar, internalExec)
            .withWorkingDirectory(Path(buildDir.path))
            .applyEnvironment(envData)

        try {
            val exitCode = processExecutor.executeAndWait(extractCommand)
            if (exitCode != 0) {
                throw RuntimeException("Failed to extract engine (exit code: $exitCode)")
            }

            createEngineFiles(buildDir, internalExec, this)
        } catch (e: Exception) {
            console.print("Failed to extract dmengine: ${e.message}\n", ERROR_OUTPUT)
            throw e
        }
    }

    private fun createEngineFiles(buildDir: File, internalExec: String, enginePath: File) {
        val extractedFile = File(buildDir, internalExec)
        if (extractedFile.exists()) {
            extractedFile.copyTo(enginePath, overwrite = true)
            makeExecutable(enginePath)

            // clean up tmp directory
            File(buildDir, "libexec").deleteRecursively()
        } else {
            throw RuntimeException("Extracted engine file not found at: ${extractedFile.absolutePath}")
        }
    }

    private fun makeExecutable(file: File) = trySilently { // Ignore on non-POSIX systems
        val permissions = setOf(
            OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, OTHERS_EXECUTE, OTHERS_READ
        )
        Files.setPosixFilePermissions(file.toPath(), permissions)
    }
}
