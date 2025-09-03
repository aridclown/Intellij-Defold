package com.aridclown.intellij.defold.debugger

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import org.jdom.Element

/**
 * Run configuration for attaching to an existing Defold game via MobDebug.
 */
class DefoldMobDebugRunConfiguration(
    project: Project,
    factory: ConfigurationFactory
) : RunConfigurationBase<Any?>(project, factory, "Defold MobDebug") {

    var host: String = "localhost"
    var port: Int = 8172
    var localRoot: String = ""
    var remoteRoot: String = ""

    override fun getConfigurationEditor(): SettingsEditor<out DefoldMobDebugRunConfiguration> =
        DefoldMobDebugSettingsEditor()

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        XmlSerializer.serializeInto(this, element)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        XmlSerializer.deserializeInto(this, element)
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        object : RunProfileState {
            val mapper = MobDebugPathMapper(mapOf(localRoot to remoteRoot))
            val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
            val debuggerManager = XDebuggerManager.getInstance(project)

            override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
                val session = debuggerManager.startSession(environment, object : XDebugProcessStarter() {
                    override fun start(session: XDebugSession) =
                        MobDebugProcess(session, project, host, port, mapper, console)
                })

                return DefaultExecutionResult(console, session.debugProcess.processHandler)
            }
        }
}
