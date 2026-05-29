package com.phoneai.assistant.ui

import android.Manifest
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.phoneai.assistant.PhoneAIApp
import com.phoneai.assistant.R
import com.phoneai.assistant.services.PhoneAIAccessibilityService
import com.phoneai.assistant.services.PhoneAIDeviceAdminReceiver
import com.phoneai.assistant.services.PhoneAIInCallService
import com.phoneai.assistant.services.PhoneAIService
import com.phoneai.assistant.utils.PrefsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var commandInput: EditText
    private lateinit var sendButton: Button
    private lateinit var startStopButton: Button
    private lateinit var permissionsCard: LinearLayout
    private lateinit var callStateView: TextView

    private val PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.POST_NOTIFICATIONS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        requestPermissions()
        checkSpecialPermissions()
        updateServiceStatus()
        observeCallState()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        checkSpecialPermissions()
    }

    private fun bindViews() {
        statusView       = findViewById(R.id.status_text)
        commandInput     = findViewById(R.id.command_input)
        sendButton       = findViewById(R.id.send_button)
        startStopButton  = findViewById(R.id.start_stop_button)
        permissionsCard  = findViewById(R.id.permissions_card)
        callStateView    = findViewById(R.id.call_state_text)

        sendButton.setOnClickListener {
            val cmd = commandInput.text.toString().trim()
            if (cmd.isNotBlank()) {
                sendCommand(cmd)
                commandInput.setText("")
            }
        }

        startStopButton.setOnClickListener {
            if (isServiceRunning()) stopService() else startService()
        }

        findViewById<Button>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.accessibility_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.overlay_button).setOnClickListener {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }

        findViewById<Button>(R.id.admin_button).setOnClickListener {
            val cn = ComponentName(this, PhoneAIDeviceAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "PhoneAI needs device admin to lock screen.")
            }
            startActivity(intent)
        }
    }

    private fun sendCommand(text: String) {
        val intent = Intent(this, PhoneAIService::class.java).apply {
            action = PhoneAIService.ACTION_COMMAND
            putExtra(PhoneAIService.EXTRA_COMMAND, text)
        }
        startService(intent)
        appendStatus("→ $text")
    }

    private fun startService() {
        val intent = Intent(this, PhoneAIService::class.java).apply {
            action = PhoneAIService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        lifecycleScope.launch {
            delay(500)
            updateServiceStatus()
        }
    }

    private fun stopService() {
        val intent = Intent(this, PhoneAIService::class.java).apply {
            action = PhoneAIService.ACTION_STOP
        }
        startService(intent)
        lifecycleScope.launch {
            delay(500)
            updateServiceStatus()
        }
    }

    private fun updateServiceStatus() {
        val running = isServiceRunning()
        val accessOk = PhoneAIAccessibilityService.instance != null
        startStopButton.text = if (running) "Stop Assistant" else "Start Assistant"
        statusView.text = buildString {
            appendLine("Service: ${if (running) "✅ Running" else "⏹ Stopped"}")
            appendLine("Accessibility: ${if (accessOk) "✅ Active" else "⚠️ Needs setup"}")
            appendLine("Wake word: \"${PrefsManager.getConfig().wakeWord}\"")
        }
    }

    private fun observeCallState() {
        lifecycleScope.launch {
            PhoneAIInCallService.callState.collect { state ->
                callStateView.text = if (state != null) {
                    "📞 ${state.state}: ${state.contactName ?: state.number}"
                } else {
                    "No active call"
                }
            }
        }
    }

    private fun appendStatus(text: String) {
        statusView.append("\n$text")
    }

    private fun isServiceRunning(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return am.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == PhoneAIService::class.java.name }
    }

    private fun requestPermissions() {
        val needed = PERMISSIONS.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1000)
        }
    }

    private fun checkSpecialPermissions() {
        val needsAccessibility = PhoneAIAccessibilityService.instance == null
        val needsOverlay       = !Settings.canDrawOverlays(this)
        val dpm                = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminCn            = ComponentName(this, PhoneAIDeviceAdminReceiver::class.java)
        val needsAdmin         = !dpm.isAdminActive(adminCn)

        permissionsCard.visibility = if (needsAccessibility || needsOverlay || needsAdmin)
            View.VISIBLE else View.GONE

        findViewById<Button>(R.id.accessibility_button).visibility =
            if (needsAccessibility) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.overlay_button).visibility =
            if (needsOverlay) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.admin_button).visibility =
            if (needsAdmin) View.VISIBLE else View.GONE
    }
}
