package com.aridclown.intellij.defold.debugger

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.openapi.project.Project
import com.aridclown.intellij.defold.ui.DefoldIcons
import com.intellij.execution.configurations.ConfigurationFactory

class DefoldMobDebugConfigurationType : ConfigurationTypeBase(
    "DefoldMobDebug",
    "Defold MobDebug",
    "Attach to a running Defold game via MobDebug",
    DefoldIcons.defoldIcon
) {

    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun getId(): String = "DefoldMobDebugFactory"

            override fun createTemplateConfiguration(project: Project) =
                DefoldMobDebugRunConfiguration(project, this)
        })
    }
}
