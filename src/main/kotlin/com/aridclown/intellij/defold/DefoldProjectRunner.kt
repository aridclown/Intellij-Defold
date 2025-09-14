package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.INI_BOOTSTRAP_SECTION
import com.aridclown.intellij.defold.DefoldConstants.INI_DEBUG_INIT_SCRIPT_KEY
import com.aridclown.intellij.defold.DefoldConstants.INI_DEBUG_INIT_SCRIPT_VALUE
import com.aridclown.intellij.defold.DefoldProjectService.Companion.getService
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import org.ini4j.Ini
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Main facade for building and launching Defold projects.
 * Orchestrates the build, extraction, and launch process.
 */
object DefoldProjectRunner {

    fun runBuild(
        project: Project,
        config: DefoldEditorConfig,
        console: ConsoleView,
        onEngineStarted: (OSProcessHandler) -> Unit
    ) {
        try {
            val processExecutor = ProcessExecutor(console)
            val builder = DefoldProjectBuilder(console, processExecutor)
            val extractor = EngineExtractor(console, processExecutor)
            val engineLauncher = EngineRunner(console, processExecutor)

            extractor.extractAndPrepareEngine(project, config).onSuccess { enginePath ->
                copyMobDebugResources(enginePath)
                updateGameProjectBootstrap(project, console)

                builder.buildProject(project, config, onBuildSuccess = {
                    engineLauncher.launchEngine(project, enginePath) // Launch the engine
                        ?.let(onEngineStarted)
                })
            }.onFailure {
                console.print("Build failed: ${it.message}\n", ERROR_OUTPUT)
            }
        } catch (e: Exception) {
            console.print("Failed to start build: ${e.message}\n", ERROR_OUTPUT)
        }
    }

    /**
     * Copy the MobDebug files into the launcher directory and create the init script.
     */
    private fun copyMobDebugResources(enginePath: File) {
        val launcherDir = enginePath.parentFile
        val classLoader = EngineRunner::class.java.classLoader

        listOf(
            "mobdebug/mobdebug.lua" to "mobdebug.lua",
            "mobdebug/mobdebug_init.lua" to "mobdebug_init.lua"
        ).forEach { (resourcePath, fileName) ->
            val targetFile = File(launcherDir, fileName)

            if (!targetFile.exists()) {
                val inputStream = classLoader.getResourceAsStream(resourcePath)
                    ?: throw IllegalStateException("$resourcePath resource not found in plugin")

                val targetPath = targetFile.toPath()
                Files.createDirectories(launcherDir.toPath())
                Files.copy(inputStream, targetPath, REPLACE_EXISTING)
            }
        }
    }

    private fun updateGameProjectBootstrap(project: Project, console: ConsoleView) {
        val gameProjectFile = project.getService().gameProjectFile ?: run {
            console.print("Warning: Game project file not found\n", ERROR_OUTPUT)
            return
        }

        try {
            gameProjectFile.inputStream.use {
                val ini = Ini(it)
                val bootstrapSection = ini[INI_BOOTSTRAP_SECTION] ?: run {
                    ini.add(INI_BOOTSTRAP_SECTION)
                    ini.get(INI_BOOTSTRAP_SECTION)!!
                }

                val debugInitScript = bootstrapSection[INI_DEBUG_INIT_SCRIPT_KEY]
                if (debugInitScript != INI_DEBUG_INIT_SCRIPT_VALUE) {
                    bootstrapSection[INI_DEBUG_INIT_SCRIPT_KEY] = INI_DEBUG_INIT_SCRIPT_VALUE

                    // Write back to the virtual file
                    WriteAction.run<Exception> {
                        gameProjectFile.getOutputStream(this).apply {
                            ini.store(this)
                            close()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            console.print("Failed to update game.project: ${e.message}\n", ERROR_OUTPUT)
        }
    }
}
