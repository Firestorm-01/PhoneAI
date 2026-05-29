package com.phoneai.assistant.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.phoneai.assistant.R
import com.phoneai.assistant.assistant.SafetyGate
import com.phoneai.assistant.services.PhoneAIService

/**
 * ConfirmationActivity
 *
 * Shown on lock screen when a dangerous action needs voice/tap confirmation.
 * Times out automatically — cancels the action on timeout.
 * User can say "confirm" / "cancel" OR tap the buttons.
 */
class ConfirmationActivity : AppCompatActivity() {

    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirmation)

        // Show on lock screen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val message    = intent.getStringExtra("message") ?: "Confirm action?"
        val actionName = intent.getStringExtra("action") ?: ""
        val timeoutMs  = intent.getLongExtra("timeout_ms", 8000L)

        findViewById<TextView>(R.id.confirm_message).text = message
        findViewById<TextView>(R.id.confirm_action_label).text = actionName

        val progressBar = findViewById<ProgressBar>(R.id.confirm_progress)
        val timerText   = findViewById<TextView>(R.id.confirm_timer)

        // ── Buttons ──
        findViewById<Button>(R.id.btn_confirm).setOnClickListener {
            timer?.cancel()
            SafetyGate.confirm()
            PhoneAIService.instance?.speak("Confirmed.")
            finish()
        }

        findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            timer?.cancel()
            SafetyGate.cancel()
            PhoneAIService.instance?.speak("Cancelled.")
            finish()
        }

        // ── Countdown ──
        progressBar.max = timeoutMs.toInt()
        timer = object : CountDownTimer(timeoutMs, 100) {
            override fun onTick(ms: Long) {
                progressBar.progress = ms.toInt()
                timerText.text = "${ms / 1000 + 1}s"
            }
            override fun onFinish() {
                // SafetyGate will auto-cancel via its own timer
                finish()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
