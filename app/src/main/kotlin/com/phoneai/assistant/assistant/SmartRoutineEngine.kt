package com.phoneai.assistant.assistant

import android.content.Context
import android.util.Log
import com.phoneai.assistant.models.ActionType
import com.phoneai.assistant.models.AssistantIntent
import java.util.Calendar

/**
 * SmartRoutineEngine
 *
 * Runs automatic routines based on time, triggers, and learned patterns.
 *
 * Built-in routines:
 *   MORNING (6-9 AM)  : Screen full brightness, unmute, announce time + battery
 *   NIGHT   (10 PM+)  : Dim screen, sleep mode, DND
 *   COMMUTE (7-9, 5-7): Drive mode, announce traffic (via Groq query)
 *   CHARGING          : Announce battery level when plugged in
 *
 * User can define custom routines via voice:
 *   "every morning at 7, read my notifications"
 *   "when I say gym mode, set volume to max and open Spotify"
 */
object SmartRoutineEngine {

    private const val TAG = "RoutineEngine"

    data class Routine(
        val id: String,
        val name: String,
        val triggerType: TriggerType,
        val triggerValue: String,         // time "07:00", event name, etc.
        val actions: List<AssistantIntent>,
        val enabled: Boolean = true,
        val lastRun: Long = 0L
    )

    enum class TriggerType {
        TIME_DAILY,     // Run at specific time every day
        TIME_WEEKDAY,   // Run Monday-Friday
        TIME_WEEKEND,   // Run Saturday-Sunday
        CHARGING_START, // When phone plugged in
        CHARGING_STOP,  // When phone unplugged
        CALL_END,       // After every call
        APP_OPEN,       // When specific app opens (via AccessibilityService)
        MANUAL          // Voice-triggered
    }

    // Built-in routines
    private val builtinRoutines = listOf(
        Routine(
            id = "morning",
            name = "Good Morning",
            triggerType = TriggerType.TIME_DAILY,
            triggerValue = "07:00",
            actions = listOf(
                AssistantIntent(ActionType.AUTO_BRIGHTNESS, mapOf()),
                AssistantIntent(ActionType.UNMUTE_PHONE, mapOf()),
                AssistantIntent(ActionType.VOLUME_SET, mapOf("volume" to "60"))
            )
        ),
        Routine(
            id = "bedtime",
            name = "Bedtime",
            triggerType = TriggerType.TIME_DAILY,
            triggerValue = "22:30",
            actions = listOf(
                AssistantIntent(ActionType.BRIGHTNESS_SET, mapOf("brightness" to "10")),
                AssistantIntent(ActionType.DO_NOT_DISTURB, mapOf()),
                AssistantIntent(ActionType.VOLUME_SET, mapOf("volume" to "20"))
            )
        ),
        Routine(
            id = "post_call",
            name = "After Call Cleanup",
            triggerType = TriggerType.CALL_END,
            triggerValue = "",
            actions = listOf(
                AssistantIntent(ActionType.UNMUTE_CALL, mapOf()),  // ensure unmuted for next call
                AssistantIntent(ActionType.SPEAKER_OFF, mapOf())
            )
        )
    )

    private val customRoutines = mutableListOf<Routine>()
    private var lastCheckedMinute = -1

    fun getAllRoutines(): List<Routine> = builtinRoutines + customRoutines

    fun addCustomRoutine(routine: Routine) {
        customRoutines.removeAll { it.id == routine.id }
        customRoutines.add(routine)
        Log.d(TAG, "Added custom routine: ${routine.name}")
    }

    fun removeRoutine(id: String) {
        customRoutines.removeAll { it.id == id }
    }

    /**
     * Called every minute by the service. Checks if any TIME routines should fire.
     */
    fun checkTimeRoutines(context: Context): List<Routine> {
        val cal = Calendar.getInstance()
        val hour   = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val dow    = cal.get(Calendar.DAY_OF_WEEK)
        val timeKey = "%02d:%02d".format(hour, minute)

        if (timeKey.hashCode() == lastCheckedMinute) return emptyList()
        lastCheckedMinute = timeKey.hashCode()

        val toRun = getAllRoutines().filter { routine ->
            if (!routine.enabled) return@filter false
            when (routine.triggerType) {
                TriggerType.TIME_DAILY -> routine.triggerValue == timeKey
                TriggerType.TIME_WEEKDAY -> routine.triggerValue == timeKey &&
                    dow in Calendar.MONDAY..Calendar.FRIDAY
                TriggerType.TIME_WEEKEND -> routine.triggerValue == timeKey &&
                    (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY)
                else -> false
            }
        }

        toRun.forEach { Log.d(TAG, "Time routine firing: ${it.name}") }
        return toRun
    }

    /**
     * Get routines for a specific event trigger.
     */
    fun getRoutinesForTrigger(trigger: TriggerType): List<Routine> {
        return getAllRoutines().filter { it.triggerType == trigger && it.enabled }
    }

    /**
     * Parse a voice command into a custom routine definition.
     * "every morning at 7 read my notifications"
     */
    fun parseRoutineCommand(command: String): Routine? {
        val lower = command.lowercase()

        val timeMatch = Regex("at (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(lower)
            ?: return null

        val hourStr   = timeMatch.groupValues[1].toIntOrNull() ?: return null
        val minuteStr = timeMatch.groupValues[2].toIntOrNull() ?: 0
        val ampm      = timeMatch.groupValues[3]

        val hour24 = when {
            ampm == "pm" && hourStr < 12 -> hourStr + 12
            ampm == "am" && hourStr == 12 -> 0
            else -> hourStr
        }

        val triggerType = when {
            lower.contains("weekday") || lower.contains("monday") -> TriggerType.TIME_WEEKDAY
            lower.contains("weekend") -> TriggerType.TIME_WEEKEND
            else -> TriggerType.TIME_DAILY
        }

        // Determine action from rest of command
        val actionIntent = when {
            lower.contains("notification") -> AssistantIntent(ActionType.SHOW_NOTIFS, mapOf())
            lower.contains("mute")         -> AssistantIntent(ActionType.MUTE_PHONE, mapOf())
            lower.contains("unmute")       -> AssistantIntent(ActionType.UNMUTE_PHONE, mapOf())
            lower.contains("dnd") || lower.contains("do not disturb") ->
                AssistantIntent(ActionType.DO_NOT_DISTURB, mapOf())
            lower.contains("brightness")   -> AssistantIntent(ActionType.AUTO_BRIGHTNESS, mapOf())
            else -> return null
        }

        val id = "custom_${System.currentTimeMillis()}"
        val timeStr = "%02d:%02d".format(hour24, minuteStr)

        return Routine(
            id = id,
            name = "Custom routine at $timeStr",
            triggerType = triggerType,
            triggerValue = timeStr,
            actions = listOf(actionIntent)
        )
    }
}
