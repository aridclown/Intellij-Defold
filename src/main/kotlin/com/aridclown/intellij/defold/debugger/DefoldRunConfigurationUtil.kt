package com.aridclown.intellij.defold.debugger

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project

object DefoldRunConfigurationUtil {

    fun getOrCreate(project: Project): RunnerAndConfigurationSettings {
        val runManager = RunManager.getInstance(project)

        runManager.allSettings
            .firstOrNull { it.configuration is MobDebugRunConfiguration }
            ?.let { return it }

        val type = ConfigurationTypeUtil.findConfigurationType(DefoldMobDebugConfigurationType::class.java)
        val factory = type.configurationFactories.single()
        val settings = runManager.createConfiguration("Defold", factory)
        runManager.addConfiguration(settings)
        if (runManager.selectedConfiguration == null) {
            runManager.selectedConfiguration = settings
        }
        return settings
    }
}
