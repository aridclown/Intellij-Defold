package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldAnnotationsManager.Companion.getInstance
import com.aridclown.intellij.defold.DefoldCoroutineService.Companion.launch
import com.intellij.openapi.actionSystem.AnActionEvent

class ReloadAnnotationsAction : DefoldProjectAction() {
    override fun actionPerformed(event: AnActionEvent) = withDefoldProject(event) { project ->
        project.launch { getInstance(project).reloadAnnotations() }
    }
}
