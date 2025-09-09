package com.aridclown.intellij.defold.ui

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

object NotificationService {
    private const val DEFAULT_GROUP = "Defold"

    fun notify(
        project: Project?,
        title: String,
        content: String,
        type: NotificationType,
        group: String = DEFAULT_GROUP,
        actionText: String? = null,
        action: ((AnActionEvent, Notification) -> Unit)? = null
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(group)
            .createNotification(title, content, type)
            .addAction(object : NotificationAction(actionText) {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    action?.invoke(e, notification)
                }
            })
            .notify(project)
    }
}