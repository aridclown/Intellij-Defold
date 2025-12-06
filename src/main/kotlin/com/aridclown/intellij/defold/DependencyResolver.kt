package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.process.ProcessExecutor
import com.intellij.openapi.project.Project

internal const val RESOLVE_BUILD_MESSAGE = "Resolving Defold dependencies"
private val RESOLVE_COMMAND = listOf("resolve")

object DependencyResolver {
    suspend fun resolve(project: Project, config: DefoldEditorConfig) {
        ProjectBuilder(ProcessExecutor()).buildProject(
            BuildRequest(
                project = project,
                config = config,
                commands = RESOLVE_COMMAND
            ),
            buildMessage = RESOLVE_BUILD_MESSAGE
        )
    }
}
