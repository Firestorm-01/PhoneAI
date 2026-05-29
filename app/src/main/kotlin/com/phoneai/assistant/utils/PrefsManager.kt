// ══════════════════════════════════════════════════════════
//  PrefsManager.kt
// ══════════════════════════════════════════════════════════
package com.phoneai.assistant.utils

import android.content.Context
import android.content.SharedPreferences
import com.phoneai.assistant.models.AssistantConfig
import org.json.JSONArray

object PrefsManager {
    private lateinit var prefs: SharedPreferences
    private const val PREF_FILE = "phoneai_prefs"

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    }

    fun getConfig(): AssistantConfig {
        val whitelist = prefs.getStringSet("auto_answer_whitelist", emptySet()) ?: emptySet()
        val emergency = prefs.getStringSet("emergency_numbers", setOf("100","101","102","112","911","999")) ?: emptySet()
        return AssistantConfig(
            groqApiKey          = prefs.getString("groq_api_key", "") ?: "",
            wakeWord            = prefs.getString("wake_word", "hey phone") ?: "hey phone",
            autoAnswerEnabled   = prefs.getBoolean("auto_answer_enabled", false),
            autoAnswerWhitelist = whitelist,
            spamBlockEnabled    = prefs.getBoolean("spam_block_enabled", true),
            confirmationTimeoutMs = prefs.getLong("confirmation_timeout_ms", 8000L),
            ttsEnabled          = prefs.getBoolean("tts_enabled", true),
            ttsSpeed            = prefs.getFloat("tts_speed", 1.0f),
            auditLogEnabled     = prefs.getBoolean("audit_log_enabled", true),
            emergencyNumbers    = emergency
        )
    }

    fun saveConfig(config: AssistantConfig) {
        prefs.edit().apply {
            putString("groq_api_key", config.groqApiKey)
            putString("wake_word", config.wakeWord)
            putBoolean("auto_answer_enabled", config.autoAnswerEnabled)
            putStringSet("auto_answer_whitelist", config.autoAnswerWhitelist)
            putBoolean("spam_block_enabled", config.spamBlockEnabled)
            putLong("confirmation_timeout_ms", config.confirmationTimeoutMs)
            putBoolean("tts_enabled", config.ttsEnabled)
            putFloat("tts_speed", config.ttsSpeed)
            putBoolean("audit_log_enabled", config.auditLogEnabled)
            putStringSet("emergency_numbers", config.emergencyNumbers)
            apply()
        }
    }
}
