package com.phoneai.assistant.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.phoneai.assistant.utils.PrefsManager

/**
 * PhoneAINotificationService
 *
 * Listens to all notifications and:
 * 1. Reads priority notifications aloud (configurable per-app)
 * 2. Provides "read my notifications" voice command
 * 3. Can dismiss notifications via voice
 * 4. Filters spam/silent-app notifications
 *
 * Requires: android.permission.BIND_NOTIFICATION_LISTENER_SERVICE
 * User must grant: Settings → Notifications → Notification Access → PhoneAI
 */
class PhoneAINotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifService"
        var instance: PhoneAINotificationService? = null

        // Priority apps whose notifications are spoken aloud
        private val ANNOUNCE_APPS = setOf(
            "com.whatsapp",
            "com.google.android.gm",
            "com.google.android.dialer",
            "org.telegram.messenger"
        )

        // Apps whose notifications are NEVER spoken
        private val SILENT_APPS = setOf(
            "com.android.systemui",
            "com.google.android.googlequicksearchbox",
            "com.android.providers.downloads"
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "Notification listener started")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg     = sbn.packageName
        val extras  = sbn.notification?.extras ?: return
        val title   = extras.getCharSequence("android.title")?.toString() ?: ""
        val body    = extras.getCharSequence("android.text")?.toString() ?: ""

        if (pkg in SILENT_APPS) return
        if (body.isBlank() && title.isBlank()) return

        Log.d(TAG, "Notification from $pkg: $title — $body")

        // Announce priority app notifications
        if (pkg in ANNOUNCE_APPS && PrefsManager.getConfig().ttsEnabled) {
            val appName = getAppName(pkg)
            val announcement = buildAnnouncement(appName, title, body)
            PhoneAIService.instance?.speak(announcement)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Optional: track dismissed notifications
    }

    /**
     * Get all current active notifications as a readable list.
     */
    fun getNotificationSummary(): String {
        val active = try { activeNotifications } catch (e: Exception) { return "Cannot read notifications." }
        if (active.isNullOrEmpty()) return "No notifications right now."

        val filtered = active
            .filter { it.packageName !in SILENT_APPS }
            .filter { it.notification?.extras?.getCharSequence("android.text") != null }
            .take(5)

        if (filtered.isEmpty()) return "No important notifications."

        return buildString {
            append("You have ${filtered.size} notifications. ")
            filtered.forEach { sbn ->
                val extras = sbn.notification.extras
                val title  = extras.getCharSequence("android.title")?.toString() ?: ""
                val body   = extras.getCharSequence("android.text")?.toString() ?: ""
                val app    = getAppName(sbn.packageName)
                if (body.isNotBlank()) append("$app: $body. ")
                else if (title.isNotBlank()) append("$app: $title. ")
            }
        }
    }

    /**
     * Dismiss all notifications (voice command: "clear all notifications")
     */
    fun dismissAll() {
        try {
            cancelAllNotifications()
            Log.d(TAG, "All notifications dismissed")
        } catch (e: Exception) {
            Log.e(TAG, "Dismiss all failed: ${e.message}")
        }
    }

    /**
     * Dismiss notifications from a specific app.
     */
    fun dismissFromApp(appName: String) {
        try {
            activeNotifications
                ?.filter { getAppName(it.packageName).lowercase().contains(appName.lowercase()) }
                ?.forEach { cancelNotification(it.key) }
        } catch (e: Exception) {
            Log.e(TAG, "Dismiss app failed: ${e.message}")
        }
    }

    private fun buildAnnouncement(appName: String, title: String, body: String): String {
        return when {
            title.isNotBlank() && body.isNotBlank() -> "From $appName. $title says: $body"
            title.isNotBlank()                       -> "$appName: $title"
            body.isNotBlank()                        -> "$appName notification: $body"
            else                                     -> "$appName sent a notification"
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
    }
}
