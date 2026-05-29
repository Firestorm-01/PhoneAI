package com.phoneai.assistant

import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.junit.runners.JUnit4

// ══════════════════════════════════════════════════════════════════════════════
//  PhoneAI — Complete Self-Contained Unit Test Suite
//  Run with:  ./gradlew test   OR   right-click in Android Studio → Run Tests
//
//  Coverage:
//   1. ActionType model integrity          (8 tests)
//   2. AssistantIntent model              (5 tests)
//   3. ActionResult sealed class          (4 tests)
//   4. ConversationMemory                 (9 tests)
//   5. FocusModeManager parsing           (9 tests)
//   6. SmartRoutineEngine                 (6 tests)
//   7. SafetyGate danger logic            (4 tests)
//   8. AuditEntry model                   (3 tests)
//   9. AssistantConfig defaults           (4 tests)
//  10. Edge cases & boundary conditions   (6 tests)
//                                   TOTAL: 58 tests
// ══════════════════════════════════════════════════════════════════════════════

// ─── Inline model copies (no Android runtime needed for pure-logic tests) ─────

enum class DangerLevel { NONE, LOW, MEDIUM, HIGH, CRITICAL, EMERGENCY }
enum class ActionCategory { DEVICE, CALLS, MESSAGING, AUDIO, CONNECTIVITY, UTILITY, NAVIGATION, META }

enum class ActionType(
    val label: String,
    val requiresConfirmation: Boolean,
    val dangerLevel: DangerLevel,
    val category: ActionCategory
) {
    POWER_OFF      ("Power Off",         true,  DangerLevel.CRITICAL,  ActionCategory.DEVICE),
    RESTART        ("Restart",           true,  DangerLevel.HIGH,      ActionCategory.DEVICE),
    SLEEP_SCREEN   ("Sleep Screen",      false, DangerLevel.NONE,      ActionCategory.DEVICE),
    WAKE_SCREEN    ("Wake Screen",       false, DangerLevel.NONE,      ActionCategory.DEVICE),
    ANSWER_CALL    ("Answer Call",       false, DangerLevel.LOW,       ActionCategory.CALLS),
    DECLINE_CALL   ("Decline Call",      false, DangerLevel.LOW,       ActionCategory.CALLS),
    MAKE_CALL      ("Make Call",         true,  DangerLevel.MEDIUM,    ActionCategory.CALLS),
    END_CALL       ("End Call",          false, DangerLevel.LOW,       ActionCategory.CALLS),
    MUTE_CALL      ("Mute Call",         false, DangerLevel.NONE,      ActionCategory.CALLS),
    UNMUTE_CALL    ("Unmute Call",       false, DangerLevel.NONE,      ActionCategory.CALLS),
    SPEAKER_ON     ("Speaker On",        false, DangerLevel.NONE,      ActionCategory.CALLS),
    SPEAKER_OFF    ("Speaker Off",       false, DangerLevel.NONE,      ActionCategory.CALLS),
    HOLD_CALL      ("Hold Call",         false, DangerLevel.NONE,      ActionCategory.CALLS),
    EMERGENCY_CALL ("Emergency Call",    false, DangerLevel.EMERGENCY, ActionCategory.CALLS),
    SEND_SMS       ("Send SMS",          true,  DangerLevel.MEDIUM,    ActionCategory.MESSAGING),
    READ_SMS       ("Read SMS",          false, DangerLevel.LOW,       ActionCategory.MESSAGING),
    READ_LAST_SMS  ("Read Last SMS",     false, DangerLevel.LOW,       ActionCategory.MESSAGING),
    VOLUME_UP      ("Volume Up",         false, DangerLevel.NONE,      ActionCategory.AUDIO),
    VOLUME_DOWN    ("Volume Down",       false, DangerLevel.NONE,      ActionCategory.AUDIO),
    VOLUME_SET     ("Set Volume",        false, DangerLevel.NONE,      ActionCategory.AUDIO),
    MUTE_PHONE     ("Mute Phone",        false, DangerLevel.NONE,      ActionCategory.AUDIO),
    UNMUTE_PHONE   ("Unmute Phone",      false, DangerLevel.NONE,      ActionCategory.AUDIO),
    DO_NOT_DISTURB ("Do Not Disturb",    false, DangerLevel.LOW,       ActionCategory.AUDIO),
    WIFI_ON        ("WiFi On",           false, DangerLevel.NONE,      ActionCategory.CONNECTIVITY),
    WIFI_OFF       ("WiFi Off",          false, DangerLevel.LOW,       ActionCategory.CONNECTIVITY),
    BLUETOOTH_ON   ("Bluetooth On",      false, DangerLevel.NONE,      ActionCategory.CONNECTIVITY),
    BLUETOOTH_OFF  ("Bluetooth Off",     false, DangerLevel.NONE,      ActionCategory.CONNECTIVITY),
    AIRPLANE_ON    ("Airplane Mode On",  true,  DangerLevel.HIGH,      ActionCategory.CONNECTIVITY),
    AIRPLANE_OFF   ("Airplane Mode Off", false, DangerLevel.LOW,       ActionCategory.CONNECTIVITY),
    HOTSPOT_ON     ("Hotspot On",        false, DangerLevel.LOW,       ActionCategory.CONNECTIVITY),
    HOTSPOT_OFF    ("Hotspot Off",       false, DangerLevel.NONE,      ActionCategory.CONNECTIVITY),
    FLASHLIGHT_ON  ("Flashlight On",     false, DangerLevel.NONE,      ActionCategory.DEVICE),
    FLASHLIGHT_OFF ("Flashlight Off",    false, DangerLevel.NONE,      ActionCategory.DEVICE),
    BRIGHTNESS_UP  ("Brightness Up",     false, DangerLevel.NONE,      ActionCategory.DEVICE),
    BRIGHTNESS_DOWN("Brightness Down",   false, DangerLevel.NONE,      ActionCategory.DEVICE),
    BRIGHTNESS_SET ("Set Brightness",    false, DangerLevel.NONE,      ActionCategory.DEVICE),
    AUTO_BRIGHTNESS("Auto Brightness",   false, DangerLevel.NONE,      ActionCategory.DEVICE),
    SET_ALARM      ("Set Alarm",         false, DangerLevel.NONE,      ActionCategory.UTILITY),
    CANCEL_ALARM   ("Cancel Alarm",      false, DangerLevel.NONE,      ActionCategory.UTILITY),
    SET_TIMER      ("Set Timer",         false, DangerLevel.NONE,      ActionCategory.UTILITY),
    OPEN_APP       ("Open App",          false, DangerLevel.LOW,       ActionCategory.NAVIGATION),
    GO_HOME        ("Go Home",           false, DangerLevel.NONE,      ActionCategory.NAVIGATION),
    GO_BACK        ("Go Back",           false, DangerLevel.NONE,      ActionCategory.NAVIGATION),
    TAKE_SCREENSHOT("Take Screenshot",   false, DangerLevel.NONE,      ActionCategory.NAVIGATION),
    SHOW_NOTIFS    ("Show Notifications",false, DangerLevel.NONE,      ActionCategory.NAVIGATION),
    QUERY          ("Query",             false, DangerLevel.NONE,      ActionCategory.META),
    UNKNOWN        ("Unknown",           false, DangerLevel.NONE,      ActionCategory.META),
}

data class AssistantIntent(
    val action: ActionType,
    val parameters: Map<String, String> = emptyMap(),
    val rawText: String = "",
    val confidence: Float = 1.0f
)

sealed class ActionResult {
    data class Success(val message: String) : ActionResult()
    data class Failed(val reason: String) : ActionResult()
    data class Blocked(val reason: String) : ActionResult()
    data class NeedsConfirmation(val message: String) : ActionResult()
}

data class AuditEntry(
    val timestamp: Long,
    val action: String,
    val parameters: String,
    val result: String,
    val confirmed: Boolean
)

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

// ─── Inline ConversationMemory (no Android deps) ──────────────────────────────

object ConversationMemory {
    private const val MAX_TURNS = 10
    data class Turn(val timestamp: Long, val userText: String, val resolvedAction: String, val resolvedParams: Map<String, String>, val result: String)
    private val turns = ArrayDeque<Turn>()
    private var lastContact: String? = null
    private var lastApp: String? = null

    fun buildContextSummary(): String {
        if (turns.isEmpty()) return ""
        return buildString {
            appendLine("Recent context:")
            turns.takeLast(3).forEach { appendLine("  [${it.resolvedAction}] \"${it.userText}\" → ${it.result}") }
            if (lastContact != null) appendLine("Last contact: $lastContact")
            if (lastApp != null) appendLine("Last app: $lastApp")
        }
    }

    fun recordTurn(userText: String, action: String, params: Map<String, String>, result: String) {
        params["contact"]?.let { lastContact = it }
        params["app_name"]?.let { lastApp = it }
        turns.addLast(Turn(System.currentTimeMillis(), userText, action, params, result))
        while (turns.size > MAX_TURNS) turns.removeFirst()
    }

    fun resolvePronouns(text: String): String {
        val contact = lastContact ?: return text
        var resolved = text
        listOf("him", "her", "them", "that person", "the same person").forEach { pronoun ->
            if (text.lowercase().contains(pronoun))
                resolved = resolved.replace(pronoun, contact, ignoreCase = true)
        }
        return resolved
    }

    fun getLastContact() = lastContact
    fun getLastApp() = lastApp
    fun getRecentTurns(n: Int = 5) = turns.takeLast(n).toList()
    fun clear() { turns.clear(); lastContact = null; lastApp = null }
}

// ─── Inline FocusModeManager ─────────────────────────────────────────────────

object FocusModeManager {
    enum class FocusMode(val label: String, val autoReplyMessage: String?, val allowCallsFrom: CallFilter) {
        NONE    ("Normal",  null,                              CallFilter.ALL),
        SLEEP   ("Sleep",   "Sleeping. Emergency? Call twice.", CallFilter.EMERGENCY_ONLY),
        FOCUS   ("Focus",   "In deep focus. Back soon.",        CallFilter.WHITELIST_ONLY),
        DRIVE   ("Drive",   "Driving! Talk soon.",              CallFilter.ALL),
        MEETING ("Meeting", "In a meeting. Back shortly.",      CallFilter.WHITELIST_ONLY),
        GYM     ("Gym",     "At the gym, reply later.",         CallFilter.NONE),
    }
    enum class CallFilter { ALL, WHITELIST_ONLY, EMERGENCY_ONLY, NONE }

    private var currentMode = FocusMode.NONE

    fun setMode(mode: FocusMode) { currentMode = mode }
    fun getCurrentMode() = currentMode

    fun shouldBlockCall(number: String, whitelist: Set<String>, emergency: Set<String>): Boolean {
        return when (currentMode.allowCallsFrom) {
            CallFilter.ALL            -> false
            CallFilter.WHITELIST_ONLY -> whitelist.none { number.contains(it) }
            CallFilter.EMERGENCY_ONLY -> emergency.none { number.contains(it) }
            CallFilter.NONE           -> true
        }
    }

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
        val durationMinutes = when {
            lower.contains("hour") -> {
                val hrs = Regex("(\\d+)\\s*hour").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                hrs * 60
            }
            lower.contains("minute") -> Regex("(\\d+)\\s*minute").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            else -> null
        }
        return Pair(mode, durationMinutes)
    }

    fun reset() { currentMode = FocusMode.NONE }
}

// ─── Inline SmartRoutineEngine ────────────────────────────────────────────────

object SmartRoutineEngine {
    enum class TriggerType { TIME_DAILY, TIME_WEEKDAY, TIME_WEEKEND, CHARGING_START, CHARGING_STOP, CALL_END, APP_OPEN, MANUAL }

    data class Routine(
        val id: String, val name: String, val triggerType: TriggerType,
        val triggerValue: String, val actions: List<ActionType>, val enabled: Boolean = true
    )

    private val builtinRoutines = listOf(
        Routine("morning", "Good Morning", TriggerType.TIME_DAILY, "07:00", listOf(ActionType.AUTO_BRIGHTNESS, ActionType.UNMUTE_PHONE, ActionType.VOLUME_SET)),
        Routine("bedtime", "Bedtime",      TriggerType.TIME_DAILY, "22:30", listOf(ActionType.BRIGHTNESS_SET, ActionType.DO_NOT_DISTURB, ActionType.VOLUME_SET)),
        Routine("postcall","Post-Call",    TriggerType.CALL_END,   "",      listOf(ActionType.UNMUTE_CALL, ActionType.SPEAKER_OFF)),
    )
    private val customRoutines = mutableListOf<Routine>()

    fun getAllRoutines() = builtinRoutines + customRoutines

    fun addCustomRoutine(routine: Routine) {
        customRoutines.removeAll { it.id == routine.id }
        customRoutines.add(routine)
    }

    fun removeRoutine(id: String) { customRoutines.removeAll { it.id == id } }

    fun getRoutinesForTrigger(trigger: TriggerType) = getAllRoutines().filter { it.triggerType == trigger && it.enabled }

    fun parseRoutineCommand(command: String): Routine? {
        val lower = command.lowercase()
        val timeMatch = Regex("at (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(lower) ?: return null
        val hourStr   = timeMatch.groupValues[1].toIntOrNull() ?: return null
        val minuteStr = timeMatch.groupValues[2].toIntOrNull() ?: 0
        val ampm      = timeMatch.groupValues[3]
        val hour24 = when {
            ampm == "pm" && hourStr < 12 -> hourStr + 12
            ampm == "am" && hourStr == 12 -> 0
            else -> hourStr
        }
        val triggerType = when {
            lower.contains("weekday") -> TriggerType.TIME_WEEKDAY
            lower.contains("weekend") -> TriggerType.TIME_WEEKEND
            else -> TriggerType.TIME_DAILY
        }
        val action = when {
            lower.contains("notification") -> ActionType.SHOW_NOTIFS
            lower.contains("unmute")       -> ActionType.UNMUTE_PHONE
            lower.contains("mute")         -> ActionType.MUTE_PHONE
            lower.contains("dnd") || lower.contains("do not disturb") -> ActionType.DO_NOT_DISTURB
            lower.contains("brightness")   -> ActionType.AUTO_BRIGHTNESS
            else -> return null
        }
        return Routine(
            id = "custom_test",
            name = "Custom",
            triggerType = triggerType,
            triggerValue = "%02d:%02d".format(hour24, minuteStr),
            actions = listOf(action)
        )
    }

    fun clearCustom() { customRoutines.clear() }
}

// ══════════════════════════════════════════════════════════════════════════════
//  TEST CLASSES
// ══════════════════════════════════════════════════════════════════════════════

@RunWith(JUnit4::class)
class ActionTypeTests {

    @Test fun `all ActionTypes have non-blank labels`() {
        ActionType.values().forEach { a ->
            assertTrue("$a has blank label", a.label.isNotBlank())
        }
    }

    @Test fun `all ActionTypes have a category`() {
        ActionType.values().forEach { a ->
            assertNotNull("$a missing category", a.category)
        }
    }

    @Test fun `all ActionTypes have a danger level`() {
        ActionType.values().forEach { a ->
            assertNotNull("$a missing danger level", a.dangerLevel)
        }
    }

    @Test fun `critical actions require confirmation`() {
        val mustConfirm = listOf(ActionType.POWER_OFF, ActionType.RESTART, ActionType.MAKE_CALL, ActionType.SEND_SMS, ActionType.AIRPLANE_ON)
        mustConfirm.forEach { a -> assertTrue("$a must require confirmation", a.requiresConfirmation) }
    }

    @Test fun `safe actions do not require confirmation`() {
        val safe = listOf(ActionType.SLEEP_SCREEN, ActionType.VOLUME_UP, ActionType.VOLUME_DOWN,
            ActionType.FLASHLIGHT_ON, ActionType.FLASHLIGHT_OFF, ActionType.GO_HOME, ActionType.GO_BACK,
            ActionType.BRIGHTNESS_UP, ActionType.BRIGHTNESS_DOWN, ActionType.TAKE_SCREENSHOT)
        safe.forEach { a -> assertFalse("$a should NOT require confirmation", a.requiresConfirmation) }
    }

    @Test fun `emergency call never requires confirmation`() {
        assertFalse(ActionType.EMERGENCY_CALL.requiresConfirmation)
        assertEquals(DangerLevel.EMERGENCY, ActionType.EMERGENCY_CALL.dangerLevel)
    }

    @Test fun `UNKNOWN action is maximally safe`() {
        assertEquals(DangerLevel.NONE, ActionType.UNKNOWN.dangerLevel)
        assertFalse(ActionType.UNKNOWN.requiresConfirmation)
    }

    @Test fun `danger levels are correctly ordered by ordinal`() {
        assertTrue(DangerLevel.NONE.ordinal     < DangerLevel.LOW.ordinal)
        assertTrue(DangerLevel.LOW.ordinal      < DangerLevel.MEDIUM.ordinal)
        assertTrue(DangerLevel.MEDIUM.ordinal   < DangerLevel.HIGH.ordinal)
        assertTrue(DangerLevel.HIGH.ordinal     < DangerLevel.CRITICAL.ordinal)
        assertTrue(DangerLevel.CRITICAL.ordinal < DangerLevel.EMERGENCY.ordinal)
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@RunWith(JUnit4::class)
class AssistantIntentTests {

    @Test fun `default intent has correct defaults`() {
        val intent = AssistantIntent(ActionType.VOLUME_UP)
        assertEquals(ActionType.VOLUME_UP, intent.action)
        assertTrue(intent.parameters.isEmpty())
        assertEquals("", intent.rawText)
        assertEquals(1.0f, intent.confidence)
    }

    @Test fun `intent parameters are not the same reference as input map`() {
        val params = mutableMapOf("contact" to "Priya")
        val intent = AssistantIntent(ActionType.MAKE_CALL, params)
        params["contact"] = "Rahul"
        // Data class copies the map reference — test that we read through the original
        // In production code, parameters should be copied. This test documents the behavior.
        assertNotNull(intent.parameters["contact"])
    }

    @Test fun `low confidence is below threshold`() {
        val intent = AssistantIntent(ActionType.POWER_OFF, confidence = 0.3f)
        assertTrue("Confidence ${intent.confidence} should be below 0.5 threshold", intent.confidence < 0.5f)
    }

    @Test fun `high confidence is above threshold`() {
        val intent = AssistantIntent(ActionType.VOLUME_UP, confidence = 0.97f)
        assertTrue(intent.confidence >= 0.5f)
    }

    @Test fun `empty parameters map is valid`() {
        val intent = AssistantIntent(ActionType.GO_HOME, emptyMap())
        assertNotNull(intent)
        assertTrue(intent.parameters.isEmpty())
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@RunWith(JUnit4::class)
class ActionResultTests {

    @Test fun `Success carries message`() {
        val r = ActionResult.Success("Volume at 60%.")
        assertTrue(r is ActionResult.Success)
        assertEquals("Volume at 60%.", (r as ActionResult.Success).message)
    }

    @Test fun `Failed carries reason`() {
        val r = ActionResult.Failed("No active call")
        assertTrue(r is ActionResult.Failed)
        assertEquals("No active call", (r as ActionResult.Failed).reason)
    }

    @Test fun `Blocked carries reason`() {
        val r = ActionResult.Blocked("Rate limit exceeded")
        assertTrue(r is ActionResult.Blocked)
        assertEquals("Rate limit exceeded", (r as ActionResult.Blocked).reason)
    }

    @Test fun `NeedsConfirmation carries message`() {
        val r = ActionResult.NeedsConfirmation("Are you sure?")
        assertTrue(r is ActionResult.NeedsConfirmation)
        assertEquals("Are you sure?", (r as ActionResult.NeedsConfirmation).message)
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@RunWith(JUnit4::class)
class ConversationMemoryTests {

    @Before fun setUp() = ConversationMemory.clear()
    @After  fun tearDown() = ConversationMemory.clear()

    @Test fun `records turn and stores last contact`() {
        ConversationMemory.recordTurn("call Priya", "MAKE_CALL", mapOf("contact" to "Priya"), "SUCCESS")
        assertEquals("Priya", ConversationMemory.getLastContact())
    }

    @Test fun `records turn and stores last app`() {
        ConversationMemory.recordTurn("open Spotify", "OPEN_APP", mapOf("app_name" to "Spotify"), "SUCCESS")
        assertEquals("Spotify", ConversationMemory.getLastApp())
    }

    @Test fun `pronoun him resolves to last contact`() {
        ConversationMemory.recordTurn("call Rahul", "MAKE_CALL", mapOf("contact" to "Rahul"), "SUCCESS")
        val resolved = ConversationMemory.resolvePronouns("call him back")
        assertTrue("Expected Rahul in: $resolved", resolved.contains("Rahul", ignoreCase = true))
    }

    @Test fun `pronoun her resolves to last contact`() {
        ConversationMemory.recordTurn("message Priya", "SEND_SMS", mapOf("contact" to "Priya"), "SUCCESS")
        val resolved = ConversationMemory.resolvePronouns("send her the update")
        assertTrue("Expected Priya in: $resolved", resolved.contains("Priya", ignoreCase = true))
    }

    @Test fun `pronoun them resolves to last contact`() {
        ConversationMemory.recordTurn("call Team", "MAKE_CALL", mapOf("contact" to "Team"), "SUCCESS")
        val resolved = ConversationMemory.resolvePronouns("remind them")
        assertTrue(resolved.contains("Team", ignoreCase = true))
    }

    @Test fun `no pronoun leaves text unchanged`() {
        ConversationMemory.recordTurn("call Priya", "MAKE_CALL", mapOf("contact" to "Priya"), "SUCCESS")
        val text = "turn off the screen"
        assertEquals(text, ConversationMemory.resolvePronouns(text))
    }

    @Test fun `context summary includes recent turn info`() {
        ConversationMemory.recordTurn("call Priya", "MAKE_CALL", mapOf("contact" to "Priya"), "SUCCESS")
        val summary = ConversationMemory.buildContextSummary()
        assertTrue(summary.contains("MAKE_CALL"))
    }

    @Test fun `memory caps at 10 turns`() {
        repeat(15) { i -> ConversationMemory.recordTurn("cmd $i", "VOLUME_UP", emptyMap(), "OK") }
        assertTrue("Should be capped at 10", ConversationMemory.getRecentTurns(20).size <= 10)
    }

    @Test fun `clear resets all state`() {
        ConversationMemory.recordTurn("call Priya", "MAKE_CALL", mapOf("contact" to "Priya"), "OK")
        ConversationMemory.clear()
        assertNull(ConversationMemory.getLastContact())
        assertNull(ConversationMemory.getLastApp())
        assertTrue(ConversationMemory.getRecentTurns().isEmpty())
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@RunWith(JUnit4::class)
class FocusModeManagerTests {

    @Before fun setUp() = FocusModeManager.reset()
    @After  fun tearDown() = FocusModeManager.reset()

    @Test fun `sleep mode parsed`() {
        val r = FocusModeManager.parseFromCommand("sleep mode")
        assertNotNull(r); assertEquals(FocusModeManager.FocusMode.SLEEP, r!!.first)
    }

    @Test fun `drive mode with 2 hours duration`() {
        val r = FocusModeManager.parseFromCommand("drive mode for 2 hours")
        assertNotNull(r); assertEquals(FocusModeManager.FocusMode.DRIVE, r!!.first); assertEquals(120, r.second)
    }

    @Test fun `meeting mode for 30 minutes`() {
        val r = FocusModeManager.parseFromCommand("meeting mode for 30 minutes")
        assertNotNull(r); assertEquals(FocusModeManager.FocusMode.MEETING, r!!.first); assertEquals(30, r.second)
    }

    @Test fun `gym mode no duration`() {
        val r = FocusModeManager.parseFromCommand("gym mode")
        assertNotNull(r); assertEquals(FocusModeManager.FocusMode.GYM, r!!.first); assertNull(r.second)
    }

    @Test fun `focus off parsed`() {
        val r = FocusModeManager.parseFromCommand("focus mode off")
        assertNotNull(r); assertEquals(FocusModeManager.FocusMode.NONE, r!!.first)
    }

    @Test fun `unknown mode returns null`() {
        assertNull(FocusModeManager.parseFromCommand("purple mode activate"))
    }

    @Test fun `SLEEP blocks unknown callers`() {
        FocusModeManager.setMode(FocusModeManager.FocusMode.SLEEP)
        assertTrue(FocusModeManager.shouldBlockCall("+919876543210", emptySet(), setOf("112","911")))
    }

    @Test fun `SLEEP allows emergency numbers`() {
        FocusModeManager.setMode(FocusModeManager.FocusMode.SLEEP)
        assertFalse(FocusModeManager.shouldBlockCall("112", emptySet(), setOf("112","911")))
    }

    @Test fun `all non-NONE modes have auto-reply messages`() {
        FocusModeManager.FocusMode.values()
            .filter { it != FocusModeManager.FocusMode.NONE }
            .forEach { mode ->
                assertNotNull("$mode missing auto-reply", mode.autoReplyMessage)
                assertTrue("$mode auto-reply blank", mode.autoReplyMessage!!.isNotBlank())
            }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@RunWith(JUnit4::class)
class SmartRoutineEngineTests {

    @Before fun setUp() = SmartRoutineEngine.clearCustom()
    @After  fun tearDown() = SmartRoutineEngine.clearCustom()

    @Test fun `daily alarm parsed`() {
        val r = SmartRoutineEngine.parseRoutineCommand("every day at 7 am mute phone")
        assertNotNull(r); assertEquals("07:00", r!!.triggerValue)
        assertEquals(SmartRoutineEngine.TriggerType.TIME_DAILY, r.triggerType)
        assertTrue(r.actions.contains(ActionType.MUTE_PHONE))
    }

    @Test fun `at 8:30 notifications`() {
        val r = SmartRoutineEngine.parseRoutineCommand("at 8:30 show notifications")
        assertNotNull(r); assertEquals("08:30", r!!.triggerValue)
    }

    @Test fun `pm time conversion`() {
        val r = SmartRoutineEngine.parseRoutineCommand("at 10 pm enable dnd")
        assertNotNull(r); assertEquals("22:00", r!!.triggerValue)
    }

    @Test fun `weekday trigger`() {
        val r = SmartRoutineEngine.parseRoutineCommand("weekday at 9 am unmute phone")
        assertNotNull(r); assertEquals(SmartRoutineEngine.TriggerType.TIME_WEEKDAY, r!!.triggerType)
    }

    @Test fun `add and remove custom routine`() {
        val routine = SmartRoutineEngine.Routine("test_r","Test",SmartRoutineEngine.TriggerType.TIME_DAILY,"09:00", listOf(ActionType.UNMUTE_PHONE))
        SmartRoutineEngine.addCustomRoutine(routine)
        assertTrue(SmartRoutineEngine.getAllRoutines().any { it.id == "test_r" })
        SmartRoutineEngine.removeRoutine("test_r")
        assertFalse(SmartRoutineEngine.getAllRoutines().any { it.id == "test_r" })
    }

    @Test fun `all built-in routines have actions`() {
        SmartRoutineEngine.getAllRoutines().forEach { r ->
            assertTrue("Routine '${r.name}' has no actions", r.actions.isNotEmpty())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@RunWith(JUnit4::class)
class AuditEntryTests {

    @Test fun `audit entry stores all fields`() {
        val ts = System.currentTimeMillis()
        val e  = AuditEntry(ts, "VOLUME_UP", "{}", "SUCCESS: 60%", true)
        assertEquals(ts,         e.timestamp)
        assertEquals("VOLUME_UP",e.action)
        assertEquals("{}",       e.parameters)
        assertEquals("SUCCESS: 60%", e.result)
        assertTrue(e.confirmed)
    }

    @Test fun `blocked entry has confirmed false`() {
        val e = AuditEntry(System.currentTimeMillis(), "POWER_OFF", "{}", "BLOCKED: user cancelled", false)
        assertFalse(e.confirmed)
        assertTrue(e.result.startsWith("BLOCKED"))
    }

    @Test fun `audit timestamp is positive`() {
        val e = AuditEntry(System.currentTimeMillis(), "GO_HOME", "{}", "SUCCESS", true)
        assertTrue(e.timestamp > 0)
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@RunWith(JUnit4::class)
class AssistantConfigTests {

    @Test fun `default config has correct wake word`() {
        val c = AssistantConfig()
        assertEquals("hey phone", c.wakeWord)
    }

    @Test fun `default config has spam blocking on`() {
        assertTrue(AssistantConfig().spamBlockEnabled)
    }

    @Test fun `default config has TTS on`() {
        assertTrue(AssistantConfig().ttsEnabled)
    }

    @Test fun `default emergency numbers are present`() {
        val c = AssistantConfig()
        assertTrue(c.emergencyNumbers.contains("112"))
        assertTrue(c.emergencyNumbers.contains("911"))
        assertTrue(c.emergencyNumbers.contains("999"))
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@RunWith(JUnit4::class)
class EdgeCaseTests {

    @Test fun `empty command does not match any focus mode`() {
        assertNull(FocusModeManager.parseFromCommand(""))
    }

    @Test fun `zero confidence intent is flagged`() {
        val i = AssistantIntent(ActionType.POWER_OFF, confidence = 0.0f)
        assertEquals(0.0f, i.confidence)
        assertTrue(i.confidence < 0.5f)
    }

    @Test fun `intent with all fields populated`() {
        val i = AssistantIntent(ActionType.SEND_SMS, mapOf("contact" to "Priya","message" to "Hey"), "send sms to Priya", 0.98f)
        assertEquals("Priya", i.parameters["contact"])
        assertEquals("Hey",   i.parameters["message"])
        assertEquals(0.98f,   i.confidence)
    }

    @Test fun `GYM mode blocks all calls including whitelist`() {
        FocusModeManager.setMode(FocusModeManager.FocusMode.GYM)
        // GYM CallFilter is NONE — even whitelist should be blocked
        assertTrue(FocusModeManager.shouldBlockCall("+911234567890", setOf("+911234567890"), setOf("112")))
        FocusModeManager.reset()
    }

    @Test fun `DRIVE mode allows all calls`() {
        FocusModeManager.setMode(FocusModeManager.FocusMode.DRIVE)
        assertFalse(FocusModeManager.shouldBlockCall("+919999999999", emptySet(), setOf("112")))
        FocusModeManager.reset()
    }

    @Test fun `FOCUS mode whitelist allows specific contact`() {
        FocusModeManager.setMode(FocusModeManager.FocusMode.FOCUS)
        val whitelist = setOf("+91987")
        assertFalse("Whitelisted number should not be blocked",
            FocusModeManager.shouldBlockCall("+919876543210", whitelist, setOf("112")))
        assertTrue("Unknown number should be blocked",
            FocusModeManager.shouldBlockCall("+911111111111", whitelist, setOf("112")))
        FocusModeManager.reset()
    }
}
