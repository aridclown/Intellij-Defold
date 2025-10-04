package com.aridclown.intellij.defold.ui

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.*
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.project.Project

object NotificationService {
    private const val DEFAULT_GROUP = "Defold"

    fun Project?.notifyInfo(title: String, content: String) {
        notify(title, content, INFORMATION)
    }

    fun Project?.notifyWarning(title: String, content: String) {
        notify(title, content, WARNING)
    }

    fun Project?.notifyError(title: String, content: String) {
        notify(title, content, ERROR)
    }

    fun Project?.notify(
        title: String,
        content: String,
        type: NotificationType,
        actions: List<NotificationAction> = emptyList()
    ) {
        getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(DEFAULT_GROUP)
                .createNotification(title, content, type)
                .addActions(actions)
                .notify(this)
        }
    }
}
