package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldConstants.DEFAULT_MOBDEBUG_PORT
import com.aridclown.intellij.defold.util.hasNoBlanks
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import org.jdom.Element
import kotlin.jvm.Transient
import com.intellij.openapi.util.JDOMExternalizerUtil

/**
 * Run configuration for attaching to an existing Defold game via MobDebug.
 * Launch/build is handled by the ProgramRunner; this class stores settings only.
 */
open class MobDebugRunConfiguration(
    project: Project,
    factory: ConfigurationFactory
) : RunConfigurationBase<Any>(project, factory, "Defold") {

    var host: String = "localhost"
    var port: Int = DEFAULT_MOBDEBUG_PORT
    var localRoot: String = ""
    var remoteRoot: String = ""
    @Transient
    var envData: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT
    @Transient
    var runtimeBuildCommands: List<String>? = null
    @Transient
    var runtimeEnableDebugScript: Boolean? = null

    fun getMappingSettings(): Map<String, String> = mapOf(localRoot to remoteRoot)
        .takeIf { it.hasNoBlanks() }
        ?: emptyMap()

    override fun checkConfiguration() {
        super.checkConfiguration()
        checkSourceRoot()
    }

    override fun getConfigurationEditor(): SettingsEditor<out MobDebugRunConfiguration> =
        DefoldMobDebugSettingsEditor()

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        JDOMExternalizerUtil.writeField(element, "host", host)
        JDOMExternalizerUtil.writeField(element, "port", port.toString())
        JDOMExternalizerUtil.writeField(element, "localRoot", localRoot)
        JDOMExternalizerUtil.writeField(element, "remoteRoot", remoteRoot)
        envData.writeExternal(element)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        host = JDOMExternalizerUtil.readField(element, "host") ?: host
        port = JDOMExternalizerUtil.readField(element, "port")?.toIntOrNull() ?: port
        localRoot = JDOMExternalizerUtil.readField(element, "localRoot") ?: localRoot
        remoteRoot = JDOMExternalizerUtil.readField(element, "remoteRoot") ?: remoteRoot
        envData = EnvironmentVariablesData.readExternal(element)
    }

    // ProgramRunner handles Debug execution. Return a minimal state for API compliance.
    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        DefoldMobDebugRunProfileState()

    private fun checkSourceRoot() {
        val hasNoSourceRoot = ModuleManager.getInstance(project)
            .modules
            .none(::hasSourceRoots)

        if (hasNoSourceRoot) throw RuntimeConfigurationError("Sources root not found.")
    }

    private fun hasSourceRoots(module: Module): Boolean =
        ModuleRootManager.getInstance(module).sourceRoots.isNotEmpty()
}
