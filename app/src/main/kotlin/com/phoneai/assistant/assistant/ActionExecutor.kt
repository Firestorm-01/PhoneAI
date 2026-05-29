package com.phoneai.assistant.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import com.phoneai.assistant.models.ActionResult
import com.phoneai.assistant.models.ActionType
import com.phoneai.assistant.models.AssistantIntent
import com.phoneai.assistant.services.PhoneAIAccessibilityService
import com.phoneai.assistant.services.PhoneAIInCallService
import com.phoneai.assistant.utils.AuditLogger
import com.phoneai.assistant.utils.ContactsHelper
import com.phoneai.assistant.utils.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ActionExecutor
 *
 * Executes all device actions after safety gate approval.
 * Each action is wrapped in try/catch with audit logging.
 */
class ActionExecutor(private val context: Context) {

    private val TAG = "ActionExecutor"

    private val audioManager   by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val telecomManager by lazy { context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager }
    private val cameraManager  by lazy { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private val powerManager   by lazy { context.getSystemService(Context.POWER_SERVICE) as PowerManager }

    private var torchEnabled = false

    suspend fun execute(intent: AssistantIntent): ActionResult = withContext(Dispatchers.Main) {
        Log.d(TAG, "Executing: ${intent.action} params=${intent.parameters}")

        val result = try {
            when (intent.action) {
                // ── Device Power ──
                ActionType.POWER_OFF     -> powerOff()
                ActionType.RESTART       -> restart()
                ActionType.SLEEP_SCREEN  -> sleepScreen()
                ActionType.WAKE_SCREEN   -> wakeScreen()

                // ── Calls ──
                ActionType.ANSWER_CALL   -> answerCall()
                ActionType.DECLINE_CALL  -> declineCall()
                ActionType.MAKE_CALL     -> makeCall(intent.parameters)
                ActionType.END_CALL      -> endCall()
                ActionType.MUTE_CALL     -> muteCall(true)
                ActionType.UNMUTE_CALL   -> muteCall(false)
                ActionType.SPEAKER_ON    -> setSpeaker(true)
                ActionType.SPEAKER_OFF   -> setSpeaker(false)
                ActionType.HOLD_CALL     -> holdCall()
                ActionType.EMERGENCY_CALL-> emergencyCall()

                // ── SMS ──
                ActionType.SEND_SMS      -> sendSms(intent.parameters)
                ActionType.READ_SMS      -> ActionResult.Success("Opening messages.")
                ActionType.READ_LAST_SMS -> readLastSms()

                // ── Volume ──
                ActionType.VOLUME_UP     -> adjustVolume(AudioManager.ADJUST_RAISE)
                ActionType.VOLUME_DOWN   -> adjustVolume(AudioManager.ADJUST_LOWER)
                ActionType.VOLUME_SET    -> setVolume(intent.parameters["volume"]?.toIntOrNull() ?: 50)
                ActionType.MUTE_PHONE    -> mutePhone(true)
                ActionType.UNMUTE_PHONE  -> mutePhone(false)
                ActionType.DO_NOT_DISTURB-> setDoNotDisturb(true)

                // ── Connectivity ──
                ActionType.WIFI_ON       -> setWifi(true)
                ActionType.WIFI_OFF      -> setWifi(false)
                ActionType.BLUETOOTH_ON  -> setBluetooth(true)
                ActionType.BLUETOOTH_OFF -> setBluetooth(false)
                ActionType.AIRPLANE_ON   -> setAirplaneMode(true)
                ActionType.AIRPLANE_OFF  -> setAirplaneMode(false)

                // ── Flashlight ──
                ActionType.FLASHLIGHT_ON  -> setFlashlight(true)
                ActionType.FLASHLIGHT_OFF -> setFlashlight(false)

                // ── Brightness ──
                ActionType.BRIGHTNESS_UP   -> adjustBrightness(+30)
                ActionType.BRIGHTNESS_DOWN -> adjustBrightness(-30)
                ActionType.BRIGHTNESS_SET  -> setBrightness(intent.parameters["brightness"]?.toIntOrNull() ?: 128)
                ActionType.AUTO_BRIGHTNESS -> setAutoBrightness(true)

                // ── Alarm & Timer ──
                ActionType.SET_ALARM    -> setAlarm(intent.parameters)
                ActionType.CANCEL_ALARM -> cancelAlarm()
                ActionType.SET_TIMER    -> setTimer(intent.parameters)

                // ── Navigation (via AccessibilityService) ──
                ActionType.GO_HOME       -> PhoneAIAccessibilityService.instance?.goHome()
                    ?: ActionResult.Failed("Accessibility service not running")
                ActionType.GO_BACK       -> PhoneAIAccessibilityService.instance?.goBack()
                    ?: ActionResult.Failed("Accessibility service not running")
                ActionType.TAKE_SCREENSHOT -> PhoneAIAccessibilityService.instance?.takeScreenshot()
                    ?: ActionResult.Failed("Accessibility service not running")
                ActionType.SHOW_NOTIFS   -> PhoneAIAccessibilityService.instance?.showNotifications()
                    ?: ActionResult.Failed("Accessibility service not running")
                ActionType.OPEN_APP      -> openApp(intent.parameters["app_name"] ?: "")

                // ── Query ──
                ActionType.QUERY -> {
                    val queryText = intent.parameters["query_text"] ?: intent.rawText
                    val answer = GroqNLPEngine.answerQuery(queryText)
                    ActionResult.Success(answer)
                }

                else -> ActionResult.Failed("Action not implemented: ${intent.action}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Action ${intent.action} threw exception", e)
            ActionResult.Failed("Error: ${e.message}")
        }

        // ── Audit log every execution ──
        if (PrefsManager.getConfig().auditLogEnabled) {
            AuditLogger.log(intent, result)
        }

        result
    }

    // ══════════════════════════════════════════════════════════
    //  DEVICE POWER
    // ══════════════════════════════════════════════════════════

    private fun powerOff(): ActionResult {
        return try {
            // Requires REBOOT permission (system app) or Device Admin
            val intent = Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN").apply {
                putExtra("android.intent.extra.KEY_CONFIRM", false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ActionResult.Success("Powering off.")
        } catch (e: Exception) {
            // Fallback: trigger power menu via AccessibilityService
            PhoneAIAccessibilityService.instance?.showPowerMenu()
                ?: ActionResult.Failed("Cannot power off: ${e.message}")
        }
    }

    private fun restart(): ActionResult {
        return try {
            val intent = Intent("android.intent.action.REBOOT").apply {
                putExtra("nowait", 1)
                putExtra("interval", 1)
                putExtra("window", 0)
            }
            context.sendBroadcast(intent)
            ActionResult.Success("Restarting.")
        } catch (e: Exception) {
            PhoneAIAccessibilityService.instance?.showPowerMenu()
                ?: ActionResult.Failed("Cannot restart: ${e.message}")
        }
    }

    private fun sleepScreen(): ActionResult {
        return PhoneAIAccessibilityService.instance?.lockScreen()
            ?: ActionResult.Failed("Accessibility service not available")
    }

    private fun wakeScreen(): ActionResult {
        val wl = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "PhoneAI:wake"
        )
        wl.acquire(3000L)
        return ActionResult.Success("Screen on.")
    }

    // ══════════════════════════════════════════════════════════
    //  CALLS
    // ══════════════════════════════════════════════════════════

    private fun answerCall(): ActionResult {
        val inCall = PhoneAIInCallService.instance
            ?: return ActionResult.Failed("No active call service")
        return inCall.answerCurrentCall()
    }

    private fun declineCall(): ActionResult {
        val inCall = PhoneAIInCallService.instance
            ?: return ActionResult.Failed("No active call service")
        return inCall.declineCurrentCall()
    }

    private fun makeCall(params: Map<String, String>): ActionResult {
        val contactInput = params["contact"]
            ?: return ActionResult.Failed("No contact specified")

        val number = if (contactInput.all { it.isDigit() || it == '+' || it == '-' }) {
            contactInput
        } else {
            ContactsHelper.findNumber(context, contactInput)
                ?: return ActionResult.Failed("Contact '$contactInput' not found")
        }

        return try {
            val uri = Uri.parse("tel:$number")
            val intent = Intent(Intent.ACTION_CALL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ActionResult.Success("Calling $contactInput.")
        } catch (e: Exception) {
            ActionResult.Failed("Call failed: ${e.message}")
        }
    }

    private fun endCall(): ActionResult {
        return PhoneAIInCallService.instance?.endCurrentCall()
            ?: ActionResult.Failed("No active call")
    }

    private fun muteCall(mute: Boolean): ActionResult {
        return PhoneAIInCallService.instance?.muteCall(mute)
            ?: ActionResult.Failed("No active call")
    }

    private fun setSpeaker(on: Boolean): ActionResult {
        return PhoneAIInCallService.instance?.setSpeaker(on)
            ?: ActionResult.Failed("No active call")
    }

    private fun holdCall(): ActionResult {
        return PhoneAIInCallService.instance?.holdCall()
            ?: ActionResult.Failed("No active call")
    }

    private fun emergencyCall(): ActionResult {
        return try {
            val uri = Uri.parse("tel:112")
            val intent = Intent(Intent.ACTION_CALL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ActionResult.Success("Calling emergency services.")
        } catch (e: Exception) {
            ActionResult.Failed("Emergency call failed: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════
    //  SMS
    // ══════════════════════════════════════════════════════════

    private fun sendSms(params: Map<String, String>): ActionResult {
        val contactInput = params["contact"]
            ?: return ActionResult.Failed("No recipient specified")
        val message = params["message"]
            ?: return ActionResult.Failed("No message body")

        val number = if (contactInput.all { it.isDigit() || it == '+' }) {
            contactInput
        } else {
            ContactsHelper.findNumber(context, contactInput)
                ?: return ActionResult.Failed("Contact '$contactInput' not found")
        }

        return try {
            val smsManager = android.telephony.SmsManager.getDefault()
            // Split long messages
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(number, null, parts, null, null)
            ActionResult.Success("Message sent to $contactInput.")
        } catch (e: Exception) {
            ActionResult.Failed("SMS failed: ${e.message}")
        }
    }

    private fun readLastSms(): ActionResult {
        return try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                null, null,
                "date DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val address = it.getString(0) ?: "Unknown"
                    val body    = it.getString(1) ?: ""
                    val name    = ContactsHelper.findName(context, address) ?: address
                    ActionResult.Success("Last message from $name: $body")
                } else {
                    ActionResult.Failed("No messages found")
                }
            } ?: ActionResult.Failed("Cannot access messages")
        } catch (e: Exception) {
            ActionResult.Failed("Read SMS failed: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════
    //  AUDIO / VOLUME
    // ══════════════════════════════════════════════════════════

    private fun adjustVolume(direction: Int): ActionResult {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max     = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val pct     = (current * 100f / max).toInt()
        return ActionResult.Success("Volume at $pct%.")
    }

    private fun setVolume(percent: Int): ActionResult {
        val clamped = percent.coerceIn(0, 100)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (clamped / 100f * max).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        return ActionResult.Success("Volume set to $clamped%.")
    }

    private fun mutePhone(mute: Boolean): ActionResult {
        audioManager.ringerMode = if (mute) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
        return ActionResult.Success(if (mute) "Phone muted." else "Phone unmuted.")
    }

    private fun setDoNotDisturb(on: Boolean): ActionResult {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(
                    if (on) android.app.NotificationManager.INTERRUPTION_FILTER_NONE
                    else android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                )
                ActionResult.Success(if (on) "Do not disturb on." else "Do not disturb off.")
            } else {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                ActionResult.Failed("Please grant Do Not Disturb access.")
            }
        } catch (e: Exception) {
            ActionResult.Failed("DND failed: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════
    //  CONNECTIVITY
    // ══════════════════════════════════════════════════════════

    @Suppress("DEPRECATION")
    private fun setWifi(on: Boolean): ActionResult {
        // On Android 10+, apps cannot directly toggle WiFi
        // Must open settings panel
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val panel = Intent(Settings.Panel.ACTION_WIFI).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(panel)
                ActionResult.Success("Opening WiFi settings.")
            } else {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiManager.isWifiEnabled = on
                ActionResult.Success(if (on) "WiFi on." else "WiFi off.")
            }
        } catch (e: Exception) {
            ActionResult.Failed("WiFi toggle failed: ${e.message}")
        }
    }

    private fun setBluetooth(on: Boolean): ActionResult {
        return try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (on) adapter?.enable() else adapter?.disable()
            ActionResult.Success(if (on) "Bluetooth on." else "Bluetooth off.")
        } catch (e: Exception) {
            ActionResult.Failed("Bluetooth toggle failed: ${e.message}")
        }
    }

    private fun setAirplaneMode(on: Boolean): ActionResult {
        return try {
            Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (on) 1 else 0)
            context.sendBroadcast(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).apply {
                putExtra("state", on)
            })
            ActionResult.Success(if (on) "Airplane mode on." else "Airplane mode off.")
        } catch (e: Exception) {
            ActionResult.Failed("Airplane mode needs WRITE_SECURE_SETTINGS: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════
    //  FLASHLIGHT
    // ══════════════════════════════════════════════════════════

    private fun setFlashlight(on: Boolean): ActionResult {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
                ?: return ActionResult.Failed("No camera found")
            cameraManager.setTorchMode(cameraId, on)
            torchEnabled = on
            ActionResult.Success(if (on) "Flashlight on." else "Flashlight off.")
        } catch (e: Exception) {
            ActionResult.Failed("Flashlight failed: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════
    //  BRIGHTNESS
    // ══════════════════════════════════════════════════════════

    private fun adjustBrightness(delta: Int): ActionResult {
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val newVal = (current + delta).coerceIn(0, 255)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, newVal)
            ActionResult.Success("Brightness adjusted.")
        } catch (e: Exception) {
            ActionResult.Failed("Brightness: ${e.message}")
        }
    }

    private fun setBrightness(percent: Int): ActionResult {
        return try {
            val clamped = percent.coerceIn(0, 100)
            val value = (clamped / 100f * 255).toInt()
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
            ActionResult.Success("Brightness set to $clamped%.")
        } catch (e: Exception) {
            ActionResult.Failed("Brightness: ${e.message}")
        }
    }

    private fun setAutoBrightness(on: Boolean): ActionResult {
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            ActionResult.Success(if (on) "Auto brightness on." else "Manual brightness on.")
        } catch (e: Exception) {
            ActionResult.Failed("Auto brightness: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════
    //  ALARM & TIMER
    // ══════════════════════════════════════════════════════════

    private fun setAlarm(params: Map<String, String>): ActionResult {
        val hour   = params["hour"]?.toIntOrNull() ?: return ActionResult.Failed("No hour specified")
        val minute = params["minute"]?.toIntOrNull() ?: 0
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            val amPm = if (hour < 12) "AM" else "PM"
            val displayHour = if (hour % 12 == 0) 12 else hour % 12
            ActionResult.Success("Alarm set for $displayHour:${minute.toString().padStart(2,'0')} $amPm.")
        } catch (e: Exception) {
            ActionResult.Failed("Alarm failed: ${e.message}")
        }
    }

    private fun cancelAlarm(): ActionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ActionResult.Success("Alarm dismissed.")
        } catch (e: Exception) {
            ActionResult.Failed("Cancel alarm: ${e.message}")
        }
    }

    private fun setTimer(params: Map<String, String>): ActionResult {
        val seconds = params["duration_seconds"]?.toIntOrNull() ?: return ActionResult.Failed("No duration")
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ActionResult.Success("Timer set for ${formatSeconds(seconds)}.")
        } catch (e: Exception) {
            ActionResult.Failed("Timer failed: ${e.message}")
        }
    }

    private fun formatSeconds(s: Int): String {
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return when {
            h > 0  -> "${h}h ${m}m"
            m > 0  -> "${m}m ${sec}s"
            else   -> "${sec}s"
        }
    }

    // ══════════════════════════════════════════════════════════
    //  APP LAUNCHING
    // ══════════════════════════════════════════════════════════

    private fun openApp(appName: String): ActionResult {
        if (appName.isBlank()) return ActionResult.Failed("No app name specified")

        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
        val match = apps.firstOrNull { app ->
            pm.getApplicationLabel(app).toString()
                .lowercase()
                .contains(appName.lowercase())
        } ?: return ActionResult.Failed("App '$appName' not found")

        return try {
            val launchIntent = pm.getLaunchIntentForPackage(match.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            } ?: return ActionResult.Failed("Cannot launch $appName")
            context.startActivity(launchIntent)
            ActionResult.Success("Opening $appName.")
        } catch (e: Exception) {
            ActionResult.Failed("Open app failed: ${e.message}")
        }
    }
}
