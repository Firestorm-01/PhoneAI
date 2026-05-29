package com.phoneai.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.phoneai.assistant.utils.AuditLogger
import com.phoneai.assistant.utils.PrefsManager

class PhoneAIApp : Application() {

    companion object {
        const val CHANNEL_ASSISTANT = "phoneai_assistant"
        const val CHANNEL_CALLS     = "phoneai_calls"
        const val CHANNEL_ALERTS    = "phoneai_alerts"

        lateinit var instance: PhoneAIApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        PrefsManager.init(this)
        AuditLogger.init(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ASSISTANT,
                "PhoneAI Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "PhoneAI persistent assistant service"
                setShowBadge(false)
            })

            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_CALLS,
                "Call Management",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming call notifications and management"
            })

            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ALERTS,
                "Assistant Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Confirmation dialogs and safety alerts"
            })
        }
    }
}
