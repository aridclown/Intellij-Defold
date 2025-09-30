package com.aridclown.intellij.defold.actions

import com.intellij.openapi.actionSystem.ActionManager

object DefoldBuildActionManager {
    fun unregister() {
        val manager = ActionManager.getInstance()

        listOf(
            "BuildMenu",
            "CompileDirty",
            "MakeModule",
            "Compile",
            "CompileFile",
            "CompileProject",
            "BuildArtifact"
        ).forEach(manager::unregisterAction)
    }
}
