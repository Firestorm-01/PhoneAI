package com.phoneai.assistant.assistant

import android.util.Log

/**
 * ConversationMemory
 *
 * Maintains a sliding window of recent intents so the NLP engine
 * has context for ambiguous follow-up commands.
 *
 * Examples:
 *   User: "call Priya"         → context: MAKE_CALL contact=Priya
 *   User: "tell her I'll be late" → resolved: SEND_SMS contact=Priya message="I'll be late"
 *
 *   User: "set volume to 50"   → context: VOLUME_SET volume=50
 *   User: "increase it a bit"  → resolved: VOLUME_SET volume=60
 */
object ConversationMemory {

    private const val TAG = "ConversationMemory"
    private const val MAX_TURNS = 10

    data class Turn(
        val timestamp: Long,
        val userText: String,
        val resolvedAction: String,
        val resolvedParams: Map<String, String>,
        val result: String
    )

    private val turns = ArrayDeque<Turn>()
    private val pronounMap = mutableMapOf<String, String>() // "her" → last female contact, etc.
    private var lastContact: String? = null
    private var lastNumber: String? = null
    private var lastApp: String? = null

    /**
     * Build a context string to inject into the NLP system prompt.
     * This gives Groq the recent history for pronoun/reference resolution.
     */
    fun buildContextSummary(): String {
        if (turns.isEmpty()) return ""
        val recent = turns.takeLast(3)
        return buildString {
            appendLine("Recent conversation context:")
            recent.forEach { turn ->
                appendLine("  [${turn.resolvedAction}] \"${turn.userText}\" → ${turn.result}")
            }
            if (lastContact != null) appendLine("Last contact mentioned: $lastContact")
            if (lastApp != null) appendLine("Last app mentioned: $lastApp")
        }
    }

    /**
     * Record a completed turn. Call this after each successful execution.
     */
    fun recordTurn(
        userText: String,
        action: String,
        params: Map<String, String>,
        result: String
    ) {
        // Track contextual references
        params["contact"]?.let { lastContact = it }
        params["app_name"]?.let { lastApp = it }
        params["number"]?.let { lastNumber = it }

        val turn = Turn(
            timestamp = System.currentTimeMillis(),
            userText = userText,
            resolvedAction = action,
            resolvedParams = params,
            result = result
        )

        turns.addLast(turn)
        while (turns.size > MAX_TURNS) turns.removeFirst()

        Log.d(TAG, "Recorded turn: $action")
    }

    /**
     * Resolve pronouns in user text using context.
     * e.g. "call him" → "call [lastContact]"
     */
    fun resolvePronouns(text: String): String {
        var resolved = text
        val contact = lastContact ?: return text
        val pronouns = listOf("him", "her", "them", "that person", "the same person")
        pronouns.forEach { pronoun ->
            if (text.lowercase().contains(pronoun)) {
                resolved = resolved.replace(pronoun, contact, ignoreCase = true)
                Log.d(TAG, "Pronoun '$pronoun' → '$contact'")
            }
        }
        return resolved
    }

    /**
     * Get last contact for follow-up commands.
     */
    fun getLastContact() = lastContact

    /**
     * Get last app name for follow-up commands.
     */
    fun getLastApp() = lastApp

    /**
     * Clear all history.
     */
    fun clear() {
        turns.clear()
        pronounMap.clear()
        lastContact = null
        lastNumber = null
        lastApp = null
    }

    fun getRecentTurns(n: Int = 5) = turns.takeLast(n)
}
