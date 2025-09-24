package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldConstants.DEFAULT_MOBDEBUG_PORT
import com.aridclown.intellij.defold.util.hasNoBlanks
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
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element

/**
 * Run configuration for attaching to an existing Defold game via MobDebug.
 * Launch/build is handled by the ProgramRunner; this class stores settings only.
 */
class MobDebugRunConfiguration(
    project: Project,
    factory: ConfigurationFactory
) : RunConfigurationBase<Any>(project, factory, "Defold") {

    var host: String = "localhost"
    var port: Int = DEFAULT_MOBDEBUG_PORT
    var localRoot: String = ""
    var remoteRoot: String = ""

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
        XmlSerializer.serializeInto(this, element)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        XmlSerializer.deserializeInto(this, element)
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
