package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.settings.DefoldSettings
import com.aridclown.intellij.defold.settings.DefoldSettingsConfigurable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

object DefoldPathResolver {

    fun ensureEditorConfig(project: Project): DefoldEditorConfig? {
        val attemptedPath = effectiveInstallPath()
        val config = DefoldEditorConfig.loadEditorConfig()
        if (config != null) return config

        val application = ApplicationManager.getApplication() ?: return null

        val openSettings = runOnUiThread(application) {
            val message = buildString {
                append("The Defold installation path could not be located.")
                attemptedPath?.let {
                    append('\n')
                    append("Checked path: ")
                    append(it)
                }
                append("\n\nWould you like to update the path now?")
            }
            Messages.showOkCancelDialog(
                project,
                message,
                "Defold",
                "Open Settings",
                Messages.getCancelButton(),
                null
            ) == Messages.YES
        }

        if (!openSettings) return null

        runOnUiThread(application) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, DefoldSettingsConfigurable::class.java)
        }

        return DefoldEditorConfig.loadEditorConfig()
    }

    private fun effectiveInstallPath(): String? {
        val settings = DefoldSettings.getInstance()
        val platform = Platform.current()
        return settings.installPath() ?: DefoldDefaults.installPathSuggestion(platform)
    }

    private fun <T> runOnUiThread(application: Application, block: () -> T): T {
        return if (application.isDispatchThread) {
            block()
        } else {
            var result: Result<T>? = null
            application.invokeAndWait {
                result = runCatching(block)
            }
            result!!.getOrThrow()
        }
    }
}
