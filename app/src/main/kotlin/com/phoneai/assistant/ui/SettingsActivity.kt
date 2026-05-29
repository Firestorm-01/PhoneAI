package com.phoneai.assistant.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.phoneai.assistant.R
import com.phoneai.assistant.models.AssistantConfig
import com.phoneai.assistant.utils.AuditLogger
import com.phoneai.assistant.utils.PrefsManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val config = PrefsManager.getConfig()

        val apiKeyInput       = findViewById<EditText>(R.id.setting_api_key)
        val wakeWordInput     = findViewById<EditText>(R.id.setting_wake_word)
        val ttsSwitch         = findViewById<Switch>(R.id.setting_tts_enabled)
        val ttsSpeedSlider    = findViewById<SeekBar>(R.id.setting_tts_speed)
        val autoAnswerSwitch  = findViewById<Switch>(R.id.setting_auto_answer)
        val spamBlockSwitch   = findViewById<Switch>(R.id.setting_spam_block)
        val auditSwitch       = findViewById<Switch>(R.id.setting_audit_log)
        val saveButton        = findViewById<Button>(R.id.settings_save_button)
        val clearAuditButton  = findViewById<Button>(R.id.btn_clear_audit)
        val auditLogView      = findViewById<TextView>(R.id.audit_log_preview)

        // Pre-fill
        apiKeyInput.setText(config.groqApiKey)
        wakeWordInput.setText(config.wakeWord)
        ttsSwitch.isChecked = config.ttsEnabled
        ttsSpeedSlider.progress = ((config.ttsSpeed - 0.5f) / 1.5f * 100).toInt()
        autoAnswerSwitch.isChecked = config.autoAnswerEnabled
        spamBlockSwitch.isChecked = config.spamBlockEnabled
        auditSwitch.isChecked = config.auditLogEnabled

        // Show recent audit log
        refreshAuditLog(auditLogView)

        saveButton.setOnClickListener {
            val newConfig = AssistantConfig(
                groqApiKey          = apiKeyInput.text.toString().trim(),
                wakeWord            = wakeWordInput.text.toString().trim().ifBlank { "hey phone" },
                ttsEnabled          = ttsSwitch.isChecked,
                ttsSpeed            = 0.5f + (ttsSpeedSlider.progress / 100f) * 1.5f,
                autoAnswerEnabled   = autoAnswerSwitch.isChecked,
                spamBlockEnabled    = spamBlockSwitch.isChecked,
                auditLogEnabled     = auditSwitch.isChecked
            )
            PrefsManager.saveConfig(newConfig)
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        }

        clearAuditButton.setOnClickListener {
            AuditLogger.clearLog()
            refreshAuditLog(auditLogView)
            Toast.makeText(this, "Audit log cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshAuditLog(view: TextView) {
        val entries = AuditLogger.getRecentEntries(20)
        if (entries.isEmpty()) {
            view.text = "No audit log entries."
            return
        }
        view.text = entries.joinToString("\n") { entry ->
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(entry.timestamp))
            "[$time] ${entry.action} → ${entry.result}"
        }
    }
}
