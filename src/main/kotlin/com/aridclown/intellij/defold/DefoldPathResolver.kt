package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.settings.DefoldSettings
import com.aridclown.intellij.defold.settings.DefoldSettingsConfigurable
import com.aridclown.intellij.defold.util.NotificationService.notify
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

object DefoldPathResolver {
    fun ensureEditorConfig(project: Project): DefoldEditorConfig? {
        val attemptedPath = effectiveInstallPath()
        var config = DefoldEditorConfig.loadEditorConfig()
        if (config != null) return config

        val message = buildString {
            append("The Defold installation path could not be located.")
            attemptedPath?.let {
                append('\n')
                append("Current location: ")
                append(it)
            }
            append("\n\nWould you like to update the path now?")
        }

        val openSettings = Messages.showOkCancelDialog(
            project,
            message,
            "Defold",
            "Open Settings",
            Messages.getCancelButton(),
            Messages.getWarningIcon()
        ) == Messages.YES

        if (!openSettings) return null

        getApplication().invokeAndWait {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, DefoldSettingsConfigurable::class.java)
        }

        config = DefoldEditorConfig.loadEditorConfig()

        if (config == null) {
            project.notify(
                title = "Invalid Defold editor path",
                content =
                buildString {
                    append("The Defold installation path could not be located. ")
                    append("Please ensure Defold is installed and the path is configured correctly.")
                },
                type = ERROR,
                expireOnActionClick = true,
                actions =
                listOf(
                    NotificationAction.createSimple("Configure") {
                        getApplication().invokeAndWait {
                            ShowSettingsUtil
                                .getInstance()
                                .showSettingsDialog(project, DefoldSettingsConfigurable::class.java)
                        }
                    }
                )
            )
            return null
        }

        return config
    }

    private fun effectiveInstallPath(): String? {
        val settings = DefoldSettings.getInstance()
        val platform = Platform.current()
        return settings.installPath() ?: DefoldDefaults.installPathSuggestion(platform)
    }
}
