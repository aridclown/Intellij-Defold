package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.INI_BOOTSTRAP_SECTION
import com.aridclown.intellij.defold.DefoldConstants.INI_DEBUG_INIT_SCRIPT_KEY
import com.aridclown.intellij.defold.DefoldConstants.INI_DEBUG_INIT_SCRIPT_VALUE
import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldProjectService
import com.aridclown.intellij.defold.engine.DefoldEngineDiscoveryService.Companion.getEngineDiscoveryService
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.aridclown.intellij.defold.util.ResourceUtil.copyResourcesToProject
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager.getInstance
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.ini4j.Ini
import org.ini4j.Profile.Section
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Main facade for building and launching Defold projects.
 * Orchestrates the build, extraction, and launch process.
 */
object DefoldProjectRunner {

    fun run(request: DefoldRunRequest) {
        val application = ApplicationManager.getApplication()
        val task = Runnable { execute(request) }

        when {
            application.isDispatchThread -> application.executeOnPooledThread(task)
            else -> task.run()
        }
    }

    /**
     * Copy debugger resources into the project workspace when missing.
     */
    private fun prepareMobDebugResources(project: Project) = copyResourcesToProject(
        project,
        EngineRunner::class.java.classLoader,
        "debugger/mobdebug.lua",
        "debugger/mobdebug_init.lua"
    )

    private fun updateGameProjectBootstrap(
        project: Project,
        console: ConsoleView,
        enableDebugScript: Boolean
    ): DebugInitScriptGuard? {
        val gameProjectFile = project.defoldProjectService().gameProjectFile ?: run {
            console.print("Warning: Game project file not found\n", ERROR_OUTPUT)
            return null
        }

        return try {
            val ini = readIni(gameProjectFile)
            val section = ini.ensureBootstrapSection()

            when {
                enableDebugScript -> {
                    // if the init script is invalid or the build folder is missing, inject the debug init script
                    if (section.shouldInjectDebugInitScript(project)) {
                        section[INI_DEBUG_INIT_SCRIPT_KEY] = INI_DEBUG_INIT_SCRIPT_VALUE
                        writeIni(gameProjectFile, ini)
                    }
                    // and clean it up on build finish
                    DebugInitScriptGuard(gameProjectFile, console)
                }

                section.containsInitScriptEntry() -> {
                    // if there is an init script entry on run, remove it
                    section.remove(INI_DEBUG_INIT_SCRIPT_KEY)
                    writeIni(gameProjectFile, ini)
                    null
                }

                else -> null
            }
        } catch (e: Exception) {
            console.print("Failed to update game.project: ${e.message}\n", ERROR_OUTPUT)
            null
        }
    }

    private fun Section.shouldInjectDebugInitScript(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        val debuggerFolder = File(basePath, "build/default/debugger")

        val isInBuild = when {
            !debuggerFolder.exists() -> true
            debuggerFolder.isDirectory -> debuggerFolder.list()?.isEmpty() ?: true
            else -> debuggerFolder.length() == 0L
        }

        return isInitDebugValueInvalid() || isInBuild
    }

    private fun readIni(gameProjectFile: VirtualFile): Ini =
        runReadAction { gameProjectFile.inputStream.use { Ini(it) } }

    private fun Ini.ensureBootstrapSection(): Section = this[INI_BOOTSTRAP_SECTION] ?: run {
        add(INI_BOOTSTRAP_SECTION)
        get(INI_BOOTSTRAP_SECTION)!!
    }

    private fun Section.containsInitScriptEntry(): Boolean = contains(INI_DEBUG_INIT_SCRIPT_KEY)

    private fun Section.isInitDebugValueInvalid(): Boolean =
        this[INI_DEBUG_INIT_SCRIPT_KEY] != INI_DEBUG_INIT_SCRIPT_VALUE

    private fun writeIni(gameProjectFile: VirtualFile, ini: Ini) = runWriteAction {
        gameProjectFile.getOutputStream(DefoldProjectRunner).use { output ->
            ini.store(output)
        }
        gameProjectFile.refresh(false, false)
    }

    private fun execute(request: DefoldRunRequest) {
        val application = ApplicationManager.getApplication()
        val services = RunnerServices(request.console)

        application.invokeAndWait(getInstance()::saveAllDocuments)
        request.project.getEngineDiscoveryService().stopActiveEngine()

        services.extractor.extractAndPrepareEngine(
            request.project, request.config, request.envData
        ).onSuccess { enginePath ->
            proceedWithBuild(request, services, enginePath)
        }.onFailure { throwable ->
            request.console.print("Build failed: ${throwable.message}\n", ERROR_OUTPUT)
        }
    }

    private fun proceedWithBuild(
        request: DefoldRunRequest,
        services: RunnerServices,
        enginePath: File
    ) {
        prepareMobDebugResources(request.project)

        val debugScriptGuard = updateGameProjectBootstrap(
            project = request.project,
            console = request.console,
            enableDebugScript = request.enableDebugScript
        )

        services.builder.buildProject(
            BuildRequest(
                project = request.project,
                config = request.config,
                envData = request.envData,
                commands = request.buildCommands,
                onSuccess = {
                    debugScriptGuard?.cleanup()
                    launchEngine(request, services.engineRunner, enginePath)
                },
                onFailure = { _ ->
                    debugScriptGuard?.cleanup()
                }
            )
        ).onFailure {
            debugScriptGuard?.cleanup()
        }
    }

    private fun launchEngine(
        request: DefoldRunRequest,
        engineRunner: EngineRunner,
        enginePath: File
    ) {
        engineRunner.launchEngine(
            project = request.project,
            enginePath = enginePath,
            enableDebugScript = request.enableDebugScript,
            debugPort = request.debugPort,
            envData = request.envData
        )?.let(request.onEngineStarted)
    }

    private class RunnerServices(console: ConsoleView) {
        private val processExecutor = ProcessExecutor(console)

        val builder = DefoldProjectBuilder(console, processExecutor)
        val extractor = EngineExtractor(console, processExecutor)
        val engineRunner = EngineRunner(console, processExecutor)
    }

    private class DebugInitScriptGuard(
        private val gameProjectFile: VirtualFile,
        private val console: ConsoleView
    ) {
        private val cleaned = AtomicBoolean(false)

        fun cleanup() {
            if (!cleaned.compareAndSet(false, true)) {
                return
            }

            try {
                val ini = readIni(gameProjectFile)
                val bootstrapSection = ini[INI_BOOTSTRAP_SECTION] ?: return
                if (!bootstrapSection.containsInitScriptEntry()) return

                runWriteAction {
                    bootstrapSection.remove(INI_DEBUG_INIT_SCRIPT_KEY)
                    gameProjectFile.getOutputStream(this).use { output ->
                        ini.store(output)
                    }
                    gameProjectFile.refresh(false, false)
                }
            } catch (e: Exception) {
                console.print("Failed to clean debug init script: ${e.message}\n", ERROR_OUTPUT)
            }
        }
    }
}

internal fun GeneralCommandLine.applyEnvironment(envData: EnvironmentVariablesData): GeneralCommandLine {
    withEnvironment(envData.envs)
    withParentEnvironmentType(
        if (envData.isPassParentEnvs) ParentEnvironmentType.CONSOLE else ParentEnvironmentType.NONE
    )
    return this
}

private inline fun runWriteAction(crossinline block: () -> Unit) {
    val app = ApplicationManager.getApplication()
    val runnable = Runnable {
        WriteAction.run<RuntimeException> { block() }
    }

    if (app.isDispatchThread) runnable.run() else app.invokeAndWait(runnable)
}
