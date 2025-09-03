package com.aridclown.intellij.defold.debugger

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.icons.AllIcons.Debugger.AttachToProcess
import com.intellij.openapi.project.Project

class DefoldMobDebugConfigurationType : ConfigurationTypeBase(
    "DefoldMobDebug",
    "Defold MobDebug",
    "Attach to a running Defold game via MobDebug",
    AttachToProcess
) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun createTemplateConfiguration(project: Project) =
                DefoldMobDebugRunConfiguration(project, this)
        })
    }
}
