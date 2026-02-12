package com.dashboard.android

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArrayList

class NotificationService : NotificationListenerService() {

    companion object {
        var instance: NotificationService? = null
            private set
    }

    interface NotificationUpdateListener {
        fun onNotificationPosted(sbn: StatusBarNotification)
        fun onNotificationRemoved(sbn: StatusBarNotification)
    }

    private val listeners = CopyOnWriteArrayList<NotificationUpdateListener>()
    private val mockNotifications = CopyOnWriteArrayList<StatusBarNotification>()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        listeners.clear()
    }

    fun addListener(listener: NotificationUpdateListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: NotificationUpdateListener) {
        listeners.remove(listener)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { notification ->
            listeners.forEach { it.onNotificationPosted(notification) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { notification ->
            listeners.forEach { it.onNotificationRemoved(notification) }
        }
    }

    fun getAllNotifications(): List<StatusBarNotification> {
        val systemNotifications = try {
            activeNotifications?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
        return systemNotifications + mockNotifications
    }

    fun addMockNotification(sbn: StatusBarNotification) {
        // Remove existing mock if key matches (update behavior)
        val existing = mockNotifications.find { it.key == sbn.key }
        if (existing != null) {
            mockNotifications.remove(existing)
        }
        mockNotifications.add(sbn)
        onNotificationPosted(sbn)
    }

    fun removeMockNotification(key: String) {
        val existing = mockNotifications.find { it.key == key }
        if (existing != null) {
            mockNotifications.remove(existing)
            onNotificationRemoved(existing)
        }
    }
}
