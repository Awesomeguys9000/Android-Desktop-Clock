package com.dashboard.android

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationService : NotificationListenerService() {

    companion object {
        var instance: NotificationService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Notification received - UI will poll for updates
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Notification removed - UI will poll for updates
    }

    fun getActiveNotifications(): Array<StatusBarNotification> {
        return try {
            activeNotifications ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
    }

    fun cancelNotification(key: String) {
        try {
            cancelNotification(key)
        } catch (e: Exception) {
            // Ignore if can't cancel
        }
    }
}
