package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.INI_BOOTSTRAP_SECTION
import com.aridclown.intellij.defold.DefoldConstants.INI_DEBUG_INIT_SCRIPT_KEY
import com.aridclown.intellij.defold.DefoldConstants.INI_DEBUG_INIT_SCRIPT_VALUE
import com.aridclown.intellij.defold.DefoldCoroutineService.Companion.launch
import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldProjectService
import com.aridclown.intellij.defold.EngineDiscoveryService.Companion.getEngineDiscoveryService
import com.aridclown.intellij.defold.process.ProcessExecutor
import com.aridclown.intellij.defold.util.ResourceUtil.copyResourcesToProject
import com.aridclown.intellij.defold.util.printError
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType.CONSOLE
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType.NONE
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Job
import org.ini4j.Config
import org.ini4j.Ini
import org.ini4j.Profile.Section
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists

/**
 * Main facade for building and launching Defold projects.
 * Orchestrates the build, extraction, and launch process.
 */
object ProjectRunner {
    fun run(request: RunRequest): Job = request.project.launch {
        val processExecutor = ProcessExecutor(request.console)
        val builder = ProjectBuilder(processExecutor)
        val extractor = EngineExtractor(processExecutor)
        val engineRunner = EngineRunner(processExecutor)
        val discoveryService = request.project.getEngineDiscoveryService()

        edtWriteAction(FileDocumentManager.getInstance()::saveAllDocuments)
        if (request.enableDebugScript && discoveryService.hasEngineForPort(request.debugPort)) {
            request.onTermination(0)
            return@launch
        }
        discoveryService.stopEnginesForPort(request.debugPort)

        proceedWithBuild(request, builder, extractor, engineRunner)
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

    private suspend fun updateGameProjectBootstrap(
        project: Project,
        console: ConsoleView,
        enableDebugScript: Boolean
    ): DebugInitScriptGuard? {
        val gameProjectFile = project.defoldProjectService().gameProjectFile ?: run {
            console.printError("Warning: Game project file not found")
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

                else -> {
                    null
                }
            }
        } catch (e: Exception) {
            console.printError("Failed to update game.project: ${e.message}")
            null
        }
    }

    private fun Section.shouldInjectDebugInitScript(project: Project): Boolean {
        val basePath = project.basePath?.let(Path::of) ?: return false
        val debuggerFolder = basePath
            .resolve("build")
            .resolve("default")
            .resolve("debugger")

        val isInBuild = when {
            debuggerFolder.notExists() -> true

            debuggerFolder.isDirectory() -> Files.newDirectoryStream(debuggerFolder).use { stream ->
                !stream.iterator().hasNext()
            }

            debuggerFolder.isRegularFile() -> debuggerFolder.fileSize() == 0L

            else -> true
        }

        return isInitDebugValueInvalid() || isInBuild
    }

    private fun readIni(gameProjectFile: VirtualFile): Ini = runReadAction {
        gameProjectFile.inputStream.use { input ->
            createIni().apply { load(input) }
        }
    }

    private fun Ini.ensureBootstrapSection(): Section = this[INI_BOOTSTRAP_SECTION] ?: run {
        add(INI_BOOTSTRAP_SECTION)
        get(INI_BOOTSTRAP_SECTION)!!
    }

    private fun Section.containsInitScriptEntry(): Boolean = contains(INI_DEBUG_INIT_SCRIPT_KEY)

    private fun Section.isInitDebugValueInvalid(): Boolean = this[INI_DEBUG_INIT_SCRIPT_KEY] != INI_DEBUG_INIT_SCRIPT_VALUE

    private suspend fun writeIni(
        gameProjectFile: VirtualFile,
        ini: Ini
    ) = edtWriteAction {
        gameProjectFile.getOutputStream(ProjectRunner).use { output ->
            ini.store(output)
        }
        gameProjectFile.refresh(false, false)
    }

    private fun createIni(): Ini = Ini().apply {
        config = Config().apply {
            isEscape = false
        }
    }

    private suspend fun proceedWithBuild(
        request: RunRequest,
        builder: ProjectBuilder,
        extractor: EngineExtractor,
        engineRunner: EngineRunner
    ) = with(request) {
        prepareMobDebugResources(request.project)

        val debugScriptGuard = updateGameProjectBootstrap(
            project,
            console,
            enableDebugScript
        )

        val buildResult = builder.buildProject(
            BuildRequest(
                project = project,
                config = config,
                envData = envData,
                commands = buildCommands,
                onFailure = onTermination
            )
        )

        debugScriptGuard?.cleanup()
        if (buildResult.isSuccess) {
            extractor.extractAndPrepareEngine(project, config, envData)
                .onSuccess { enginePath ->
                    val handler = engineRunner.launchEngine(request, enginePath)
                    if (handler == null) {
                        onTermination(-1)
                        return@with
                    }

                    handler.let(onEngineStarted)
                }.onFailure { throwable ->
                    console.printError("Failed to prepare engine: ${throwable.message}")
                    onTermination(-1)
                }
            return@with
        }

        buildResult.exceptionOrNull()?.let { throwable ->
            if (throwable !is BuildProcessFailedException) {
                console.printError("Build failed: ${throwable.message ?: throwable.javaClass.simpleName}")
                onTermination(-1)
            }
        }
    }

    private class DebugInitScriptGuard(
        private val gameProjectFile: VirtualFile,
        private val console: ConsoleView
    ) {
        private val cleaned = AtomicBoolean(false)

        suspend fun cleanup() {
            if (!cleaned.compareAndSet(false, true)) {
                return
            }

            try {
                val ini = readIni(gameProjectFile)
                val bootstrapSection = ini[INI_BOOTSTRAP_SECTION] ?: return
                if (!bootstrapSection.containsInitScriptEntry()) return

                edtWriteAction {
                    bootstrapSection.remove(INI_DEBUG_INIT_SCRIPT_KEY)
                    gameProjectFile.getOutputStream(this).use { output ->
                        ini.store(output)
                    }
                    gameProjectFile.refresh(false, false)
                }
            } catch (e: Exception) {
                console.printError("Failed to clean debug init script: ${e.message}")
            }
        }
    }
}

internal fun GeneralCommandLine.applyEnvironment(envData: EnvironmentVariablesData): GeneralCommandLine {
    withEnvironment(envData.envs)
    withParentEnvironmentType(
        if (envData.isPassParentEnvs) CONSOLE else NONE
    )
    return this
}
