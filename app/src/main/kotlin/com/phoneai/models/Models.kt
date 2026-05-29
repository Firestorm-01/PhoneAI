package com.phoneai.assistant.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// ══════════════════════════════════════════════════════════
//  INTENT MODEL — parsed from NLP
// ══════════════════════════════════════════════════════════

data class AssistantIntent(
    val action: ActionType,
    val parameters: Map<String, String> = emptyMap(),
    val rawText: String = "",
    val confidence: Float = 1.0f
)

enum class ActionType(
    val label: String,
    val requiresConfirmation: Boolean,
    val dangerLevel: DangerLevel,
    val category: ActionCategory
) {
    // ── Phone Power ──
    POWER_OFF       ("Power Off",        true,  DangerLevel.CRITICAL,  ActionCategory.DEVICE),
    RESTART         ("Restart",          true,  DangerLevel.HIGH,      ActionCategory.DEVICE),
    SLEEP_SCREEN    ("Sleep Screen",     false, DangerLevel.NONE,      ActionCategory.DEVICE),
    WAKE_SCREEN     ("Wake Screen",      false, DangerLevel.NONE,      ActionCategory.DEVICE),

    // ── Calls ──
    ANSWER_CALL     ("Answer Call",      false, DangerLevel.LOW,       ActionCategory.CALLS),
    DECLINE_CALL    ("Decline Call",     false, DangerLevel.LOW,       ActionCategory.CALLS),
    MAKE_CALL       ("Make Call",        true,  DangerLevel.MEDIUM,    ActionCategory.CALLS),
    END_CALL        ("End Call",         false, DangerLevel.LOW,       ActionCategory.CALLS),
    MUTE_CALL       ("Mute Call",        false, DangerLevel.NONE,      ActionCategory.CALLS),
    UNMUTE_CALL     ("Unmute Call",      false, DangerLevel.NONE,      ActionCategory.CALLS),
    SPEAKER_ON      ("Speaker On",       false, DangerLevel.NONE,      ActionCategory.CALLS),
    SPEAKER_OFF     ("Speaker Off",      false, DangerLevel.NONE,      ActionCategory.CALLS),
    HOLD_CALL       ("Hold Call",        false, DangerLevel.NONE,      ActionCategory.CALLS),
    EMERGENCY_CALL  ("Emergency Call",   false, DangerLevel.EMERGENCY, ActionCategory.CALLS),

    // ── Messaging ──
    SEND_SMS        ("Send SMS",         true,  DangerLevel.MEDIUM,    ActionCategory.MESSAGING),
    READ_SMS        ("Read SMS",         false, DangerLevel.LOW,       ActionCategory.MESSAGING),
    READ_LAST_SMS   ("Read Last SMS",    false, DangerLevel.LOW,       ActionCategory.MESSAGING),

    // ── Volume & Audio ──
    VOLUME_UP       ("Volume Up",        false, DangerLevel.NONE,      ActionCategory.AUDIO),
    VOLUME_DOWN     ("Volume Down",      false, DangerLevel.NONE,      ActionCategory.AUDIO),
    VOLUME_SET      ("Set Volume",       false, DangerLevel.NONE,      ActionCategory.AUDIO),
    MUTE_PHONE      ("Mute Phone",       false, DangerLevel.NONE,      ActionCategory.AUDIO),
    UNMUTE_PHONE    ("Unmute Phone",     false, DangerLevel.NONE,      ActionCategory.AUDIO),
    DO_NOT_DISTURB  ("Do Not Disturb",   false, DangerLevel.LOW,       ActionCategory.AUDIO),

    // ── Connectivity ──
    WIFI_ON         ("WiFi On",          false, DangerLevel.NONE,      ActionCategory.CONNECTIVITY),
    WIFI_OFF        ("WiFi Off",         false, DangerLevel.LOW,       ActionCategory.CONNECTIVITY),
    BLUETOOTH_ON    ("Bluetooth On",     false, DangerLevel.NONE,      ActionCategory.CONNECTIVITY),
    BLUETOOTH_OFF   ("Bluetooth Off",    false, DangerLevel.NONE,      ActionCategory.CONNECTIVITY),
    AIRPLANE_ON     ("Airplane Mode On", true,  DangerLevel.HIGH,      ActionCategory.CONNECTIVITY),
    AIRPLANE_OFF    ("Airplane Mode Off",false, DangerLevel.LOW,       ActionCategory.CONNECTIVITY),
    HOTSPOT_ON      ("Hotspot On",       false, DangerLevel.LOW,       ActionCategory.CONNECTIVITY),
    HOTSPOT_OFF     ("Hotspot Off",      false, DangerLevel.NONE,      ActionCategory.CONNECTIVITY),

    // ── Flashlight ──
    FLASHLIGHT_ON   ("Flashlight On",    false, DangerLevel.NONE,      ActionCategory.DEVICE),
    FLASHLIGHT_OFF  ("Flashlight Off",   false, DangerLevel.NONE,      ActionCategory.DEVICE),

    // ── Brightness ──
    BRIGHTNESS_UP   ("Brightness Up",    false, DangerLevel.NONE,      ActionCategory.DEVICE),
    BRIGHTNESS_DOWN ("Brightness Down",  false, DangerLevel.NONE,      ActionCategory.DEVICE),
    BRIGHTNESS_SET  ("Set Brightness",   false, DangerLevel.NONE,      ActionCategory.DEVICE),
    AUTO_BRIGHTNESS ("Auto Brightness",  false, DangerLevel.NONE,      ActionCategory.DEVICE),

    // ── Alarm & Timer ──
    SET_ALARM       ("Set Alarm",        false, DangerLevel.NONE,      ActionCategory.UTILITY),
    CANCEL_ALARM    ("Cancel Alarm",     false, DangerLevel.NONE,      ActionCategory.UTILITY),
    SET_TIMER       ("Set Timer",        false, DangerLevel.NONE,      ActionCategory.UTILITY),

    // ── Navigation ──
    OPEN_APP        ("Open App",         false, DangerLevel.LOW,       ActionCategory.NAVIGATION),
    GO_HOME         ("Go Home",          false, DangerLevel.NONE,      ActionCategory.NAVIGATION),
    GO_BACK         ("Go Back",          false, DangerLevel.NONE,      ActionCategory.NAVIGATION),
    TAKE_SCREENSHOT ("Take Screenshot",  false, DangerLevel.NONE,      ActionCategory.NAVIGATION),
    SHOW_NOTIFS     ("Show Notifications",false,DangerLevel.NONE,      ActionCategory.NAVIGATION),

    // ── Meta ──
    QUERY           ("Query",            false, DangerLevel.NONE,      ActionCategory.META),
    UNKNOWN         ("Unknown",          false, DangerLevel.NONE,      ActionCategory.META);
}

enum class DangerLevel { NONE, LOW, MEDIUM, HIGH, CRITICAL, EMERGENCY }

enum class ActionCategory { DEVICE, CALLS, MESSAGING, AUDIO, CONNECTIVITY, UTILITY, NAVIGATION, META }

// ══════════════════════════════════════════════════════════
//  ACTION RESULT
// ══════════════════════════════════════════════════════════

sealed class ActionResult {
    data class Success(val message: String) : ActionResult()
    data class Failed(val reason: String) : ActionResult()
    data class NeedsConfirmation(
        val message: String,
        val action: ActionType,
        val onConfirm: () -> ActionResult
    ) : ActionResult()
    data class Blocked(val reason: String) : ActionResult()
}

// ══════════════════════════════════════════════════════════
//  AUDIT LOG ENTRY
// ══════════════════════════════════════════════════════════

@Parcelize
data class AuditEntry(
    val timestamp: Long,
    val action: String,
    val parameters: String,
    val result: String,
    val confirmed: Boolean
) : Parcelable

// ══════════════════════════════════════════════════════════
//  CALL STATE
// ══════════════════════════════════════════════════════════

data class CallState(
    val number: String,
    val contactName: String?,
    val state: CallStateType,
    val isMuted: Boolean = false,
    val isOnSpeaker: Boolean = false,
    val isOnHold: Boolean = false,
    val startTime: Long? = null
)

enum class CallStateType { RINGING, ACTIVE, HOLDING, DISCONNECTED }

// ══════════════════════════════════════════════════════════
//  ASSISTANT CONFIG (user prefs)
// ══════════════════════════════════════════════════════════

data class AssistantConfig(
    val groqApiKey: String = "",
    val wakeWord: String = "hey phone",
    val autoAnswerEnabled: Boolean = false,
    val autoAnswerWhitelist: Set<String> = emptySet(),
    val spamBlockEnabled: Boolean = true,
    val confirmationTimeoutMs: Long = 8000L,
    val ttsEnabled: Boolean = true,
    val ttsSpeed: Float = 1.0f,
    val auditLogEnabled: Boolean = true,
    val emergencyNumbers: Set<String> = setOf("100", "101", "102", "112", "911", "999")
)
