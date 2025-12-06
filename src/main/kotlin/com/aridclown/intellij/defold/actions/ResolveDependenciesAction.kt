package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldCoroutineService.Companion.launch
import com.aridclown.intellij.defold.DependencyResolver
import com.intellij.openapi.actionSystem.AnActionEvent

class ResolveDependenciesAction : DefoldProjectAction() {
    override fun actionPerformed(event: AnActionEvent) = withDefoldProject(event) { project ->
        withDefoldConfig(project) { config ->
            project.launch {
                DependencyResolver.resolve(project, config)
            }
        }
    }
}
