package com.aridclown.intellij.defold.util

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.*
import com.intellij.openapi.project.Project

object NotificationService {
    private const val DEFAULT_GROUP = "Defold"

    fun Project?.notifyInfo(
        title: String,
        content: String
    ) {
        notify(title, content, INFORMATION)
    }

    fun Project?.notifyWarning(
        title: String,
        content: String
    ) {
        notify(title, content, WARNING)
    }

    fun Project?.notifyError(
        title: String,
        content: String
    ) {
        notify(title, content, ERROR)
    }

    fun Project?.notify(
        title: String,
        content: String,
        type: NotificationType,
        actions: List<NotificationAction> = emptyList(),
        expireOnActionClick: Boolean = false
    ) {
        val notification =
            NotificationGroupManager
                .getInstance()
                .getNotificationGroup(DEFAULT_GROUP)
                .createNotification(title, content, type)

        if (expireOnActionClick) {
            actions.forEach { action ->
                notification.addAction(
                    NotificationAction.create(action.templateText) { event ->
                        action.actionPerformed(event, notification)
                        notification.expire()
                    }
                )
            }
        } else {
            notification.addActions(actions)
        }

        notification.notify(this)
    }
}
