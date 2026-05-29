package com.phoneai.assistant.assistant

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import com.phoneai.assistant.models.ActionResult
import java.util.Calendar

/**
 * FocusModeManager
 *
 * Smart focus profiles that combine DND, volume, auto-replies, and scheduled activation.
 *
 * Built-in profiles:
 *   - SLEEP     : Full silence, screen off, emergency-only calls
 *   - FOCUS     : Notifications silenced, calls from whitelist only
 *   - DRIVE     : Auto-SMS reply, hands-free calls, max volume
 *   - MEETING   : Vibrate only, no notifications, calendar-aware
 *   - GYM       : Media volume max, calls silenced, music on
 *
 * Voice commands:
 *   "focus mode on", "sleep mode", "drive mode", "gym mode", "focus off"
 */
object FocusModeManager {

    private const val TAG = "FocusModeManager"

    enum class FocusMode(
        val label: String,
        val icon: String,
        val autoReplyMessage: String?,
        val ringerMode: Int,
        val mediaVolumePct: Int,
        val allowCallsFrom: CallFilter
    ) {
        NONE    ("Off",      "🔔", null,                              AudioManager.RINGER_MODE_NORMAL, 50,  CallFilter.ALL),
        SLEEP   ("Sleep",    "😴", "Sleeping. Emergency? Call twice.", AudioManager.RINGER_MODE_SILENT, 0,   CallFilter.EMERGENCY_ONLY),
        FOCUS   ("Focus",    "🎯", "In deep focus. Back soon.",        AudioManager.RINGER_MODE_VIBRATE, 30, CallFilter.WHITELIST_ONLY),
        DRIVE   ("Drive",    "🚗", "Driving! Talk soon.",              AudioManager.RINGER_MODE_NORMAL, 80,  CallFilter.ALL),
        MEETING ("Meeting",  "📅", "In a meeting. Back shortly.",      AudioManager.RINGER_MODE_VIBRATE, 0,  CallFilter.WHITELIST_ONLY),
        GYM     ("Gym",      "💪", "At the gym, reply later.",         AudioManager.RINGER_MODE_SILENT, 100, CallFilter.NONE);
    }

    enum class CallFilter { ALL, WHITELIST_ONLY, EMERGENCY_ONLY, NONE }

    private var currentMode = FocusMode.NONE
    private var autoReplyEnabled = false
    private var scheduledEndTime: Long? = null

    fun activate(context: Context, mode: FocusMode, durationMinutes: Int? = null): ActionResult {
        Log.d(TAG, "Activating focus mode: $mode")

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm    = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Apply ringer
        audio.ringerMode = mode.ringerMode

        // Apply media volume
        val maxMedia = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetMedia = (mode.mediaVolumePct / 100f * maxMedia).toInt()
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, targetMedia, 0)

        // Apply DND
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(
                when (mode) {
                    FocusMode.SLEEP, FocusMode.FOCUS, FocusMode.MEETING ->
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    FocusMode.GYM ->
                        NotificationManager.INTERRUPTION_FILTER_NONE
                    else ->
                        NotificationManager.INTERRUPTION_FILTER_ALL
                }
            )
        }

        currentMode = mode
        autoReplyEnabled = mode.autoReplyMessage != null

        // Schedule auto-deactivation
        if (durationMinutes != null && durationMinutes > 0) {
            val endTime = System.currentTimeMillis() + durationMinutes * 60_000L
            scheduledEndTime = endTime
            scheduleDeactivation(context, endTime)
        }

        val duration = if (durationMinutes != null) " for $durationMinutes minutes" else ""
        return ActionResult.Success("${mode.icon} ${mode.label} mode activated$duration.")
    }

    fun deactivate(context: Context): ActionResult {
        scheduledEndTime = null
        return activate(context, FocusMode.NONE)
    }

    fun getCurrentMode() = currentMode

    fun getAutoReplyMessage() = if (autoReplyEnabled) currentMode.autoReplyMessage else null

    fun shouldBlockCall(number: String, whitelist: Set<String>, emergency: Set<String>): Boolean {
        return when (currentMode.allowCallsFrom) {
            CallFilter.ALL              -> false
            CallFilter.WHITELIST_ONLY   -> whitelist.none { number.contains(it) }
            CallFilter.EMERGENCY_ONLY   -> emergency.none { number.contains(it) }
            CallFilter.NONE             -> true
        }
    }

    private fun scheduleDeactivation(context: Context, endTimeMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent("com.phoneai.FOCUS_MODE_END").let {
            PendingIntent.getBroadcast(context, 999, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTimeMs, intent)
        Log.d(TAG, "Scheduled focus mode end at $endTimeMs")
    }

    /**
     * Parse "sleep mode", "focus mode for 2 hours", etc.
     */
    fun parseFromCommand(command: String): Pair<FocusMode, Int?>? {
        val lower = command.lowercase()
        val mode = when {
            lower.contains("sleep")   -> FocusMode.SLEEP
            lower.contains("focus")   -> FocusMode.FOCUS
            lower.contains("drive")   -> FocusMode.DRIVE
            lower.contains("meeting") -> FocusMode.MEETING
            lower.contains("gym")     -> FocusMode.GYM
            lower.contains("off") || lower.contains("normal") -> FocusMode.NONE
            else -> return null
        }

        // Extract duration: "for 2 hours", "for 30 minutes", "for an hour"
        val durationMinutes = when {
            lower.contains("hour") -> {
                val hrs = Regex("(\\d+)\\s*hour").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                hrs * 60
            }
            lower.contains("minute") -> {
                Regex("(\\d+)\\s*minute").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            }
            else -> null
        }

        return Pair(mode, durationMinutes)
    }
}
