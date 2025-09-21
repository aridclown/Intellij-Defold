package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldEditorConfig
import com.aridclown.intellij.defold.DefoldProjectRunner
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

private typealias BuildLauncher = (
    project: Project,
    config: DefoldEditorConfig,
    console: ConsoleView,
    onEngineStarted: (OSProcessHandler) -> Unit
) -> Unit

open class DefoldProjectRunProgramRunner(
    private val consoleProvider: (Project) -> ConsoleView = { project ->
        TextConsoleBuilderFactory.getInstance()
            .createBuilder(project)
            .console
    },
    private val configLoader: () -> DefoldEditorConfig? = { DefoldEditorConfig.loadEditorConfig() },
    private val buildLauncher: BuildLauncher = { project, config, console, onEngineStarted ->
        DefoldProjectRunner.runBuild(project, config, console, onEngineStarted)
    },
    private val edtInvoker: (() -> Unit) -> Unit = { task ->
        ApplicationManager.getApplication().invokeLater(task)
    }
) : GenericProgramRunner<RunnerSettings>() {

    companion object {
        const val DEFOLD_RUNNER_ID = "DefoldProjectRunRunner"
    }

    override fun getRunnerId(): String = DEFOLD_RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultRunExecutor.EXECUTOR_ID && profile is DefoldMobDebugRunConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor =
        with(environment) {
            val console = consoleProvider(project)

            val descriptor = RunContentDescriptor(console, null, console.component, runProfile.name).apply {
                executionId = environment.executionId
            }

            val defoldCfg = configLoader()
            if (defoldCfg == null) {
                console.print("Invalid Defold editor path.\n", ERROR_OUTPUT)
            } else {
                buildLauncher(
                    project,
                    defoldCfg,
                    console
                ) { handler ->
                    edtInvoker {
                        descriptor.processHandler = handler
                    }
                }
            }

            descriptor
        }
}
