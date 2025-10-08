package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.ui.DefoldIcons
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfigurationSingletonPolicy
import com.intellij.execution.configurations.RunConfigurationSingletonPolicy.SINGLE_INSTANCE_ONLY
import com.intellij.openapi.project.Project

class DefoldMobDebugConfigurationType : ConfigurationTypeBase(
    "DefoldMobDebug",
    "Defold",
    "Attach to a running Defold game via MobDebug",
    DefoldIcons.defoldIcon
) {

    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun getId(): String = "DefoldMobDebugFactory"

            override fun createTemplateConfiguration(project: Project) =
                MobDebugRunConfiguration(project, this)

            override fun getSingletonPolicy(): RunConfigurationSingletonPolicy = SINGLE_INSTANCE_ONLY
        })
    }
}
