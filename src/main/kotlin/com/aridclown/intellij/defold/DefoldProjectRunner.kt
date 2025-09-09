package com.aridclown.intellij.defold

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.openapi.project.Project

/**
 * Main facade for building and launching Defold projects.
 * Orchestrates the build, extraction, and launch process.
 */
object DefoldProjectRunner {

    fun runBuild(project: Project, defoldConfig: DefoldEditorConfig, console: ConsoleView) {
        try {
            val processExecutor = ProcessExecutor(console)
            val builder = DefoldProjectBuilder(console, processExecutor)
            val extractor = EngineExtractor(console, processExecutor)
            val engineLauncher = EngineRunner(console, processExecutor)

            builder.buildProject(project, defoldConfig) {
                launchAfterBuild(project, defoldConfig, extractor, engineLauncher)
            }.onFailure {
                console.print("Build failed: ${it.message}\n", ERROR_OUTPUT)
            }
        } catch (e: Exception) {
            console.print("Failed to start build: ${e.message}\n", ERROR_OUTPUT)
        }
    }

    private fun launchAfterBuild(
        project: Project,
        config: DefoldEditorConfig,
        extractor: EngineExtractor,
        engineLauncher: EngineRunner
    ) {
        extractor.extractAndPrepareEngine(project, config)
            .onSuccess { enginePath ->
                engineLauncher.launchEngine(project, enginePath)
            }
            .onFailure { _ ->
                // Error already logged in EngineExtractor
            }
    }
}
