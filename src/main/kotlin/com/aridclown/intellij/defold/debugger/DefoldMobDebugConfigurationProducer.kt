package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

class DefoldMobDebugConfigurationProducer : LazyRunConfigurationProducer<MobDebugRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        val type = ConfigurationTypeUtil.findConfigurationType(DefoldMobDebugConfigurationType::class.java)
        return type.configurationFactories.first()
    }

    override fun setupConfigurationFromContext(
        configuration: MobDebugRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val project = context.project
        val basePath = project.basePath ?: return false
        val virtualFile = context.virtualFile() ?: return false

        if (!virtualFile.isDirectory || !virtualFile.isProjectRoot(basePath)) return false
        if (!project.isDefoldProject) return false

        configuration.name = project.name
        configuration.localRoot = basePath
        configuration.remoteRoot = configuration.remoteRoot.ifBlank { "" }
        return true
    }

    override fun isConfigurationFromContext(
        configuration: MobDebugRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val project = context.project
        val basePath = project.basePath ?: return false
        val virtualFile = context.virtualFile() ?: return false

        return virtualFile.isDirectory && virtualFile.isProjectRoot(basePath) && configuration.localRoot == basePath
    }

    private fun ConfigurationContext.virtualFile(): VirtualFile? =
        location?.virtualFile ?: CommonDataKeys.VIRTUAL_FILE.getData(dataContext)

    private fun VirtualFile.isProjectRoot(projectBasePath: String): Boolean =
        path == projectBasePath || canonicalPath == projectBasePath
}
