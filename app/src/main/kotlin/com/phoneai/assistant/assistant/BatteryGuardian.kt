package com.phoneai.assistant.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.phoneai.assistant.services.PhoneAIService

/**
 * BatteryGuardian
 *
 * Monitors battery level and proactively alerts the user via TTS.
 * Also provides voice-query responses ("what's my battery?")
 * and can trigger power-saving mode automatically.
 *
 * Features:
 * - Low battery alerts at configurable thresholds (20%, 10%, 5%)
 * - Charging complete alert
 * - "Battery critical" hands-free warning
 * - Power-save mode auto-activation below threshold
 * - Battery health report via voice query
 */
object BatteryGuardian {

    private const val TAG = "BatteryGuardian"

    data class BatteryStatus(
        val level: Int,           // 0-100
        val isCharging: Boolean,
        val chargingType: String, // USB, AC, Wireless, None
        val temperature: Float,   // Celsius
        val voltage: Float,       // Volts
        val health: String        // Good, Overheat, Dead, etc.
    )

    private var lastAlertLevel = 100
    private var registered = false
    private var currentStatus: BatteryStatus? = null

    // Thresholds in %
    var alertAt20 = true
    var alertAt10 = true
    var alertAt5  = true
    var autoLowPowerAt10 = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return

            val level   = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale   = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val pct     = (level * 100f / scale).toInt()
            val status  = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val temp    = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f
            val health  = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
                BatteryManager.BATTERY_HEALTH_GOOD        -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT    -> "Overheating"
                BatteryManager.BATTERY_HEALTH_DEAD        -> "Dead"
                BatteryManager.BATTERY_HEALTH_COLD        -> "Cold"
                else                                       -> "Unknown"
            }

            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL
            val chargingType = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC       -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB      -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "None"
            }

            val prevStatus = currentStatus
            currentStatus = BatteryStatus(pct, isCharging, chargingType, temp, voltage, health)

            Log.d(TAG, "Battery: $pct% charging=$isCharging type=$chargingType temp=${temp}°C")

            handleAlerts(context, pct, isCharging, prevStatus, health)
        }
    }

    fun register(context: Context) {
        if (registered) return
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        registered = true
        Log.d(TAG, "BatteryGuardian registered")
    }

    fun unregister(context: Context) {
        if (!registered) return
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        registered = false
    }

    fun getStatus(context: Context): BatteryStatus? {
        if (currentStatus != null) return currentStatus
        // Query on-demand
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level   = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale   = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct     = (level * 100f / scale).toInt()
        val status  = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val temp    = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val chargingType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC       -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB      -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "None"
        }
        return BatteryStatus(pct, isCharging, chargingType, temp, voltage, "Good").also { currentStatus = it }
    }

    fun buildSpeechResponse(context: Context): String {
        val s = getStatus(context) ?: return "Battery status unavailable."
        val charging = if (s.isCharging) "charging via ${s.chargingType}" else "not charging"
        val tempWarn = if (s.temperature > 40f) " Warning: battery is hot at ${s.temperature}°C." else ""
        return "Battery is at ${s.level}%, $charging.$tempWarn"
    }

    private fun handleAlerts(
        context: Context,
        pct: Int,
        isCharging: Boolean,
        prev: BatteryStatus?,
        health: String
    ) {
        val svc = PhoneAIService.instance ?: return

        // Charging complete
        if (prev?.isCharging == true && !isCharging && pct >= 95) {
            svc.speak("Battery fully charged. You can unplug now.")
            lastAlertLevel = pct
            return
        }

        // Overheating warning
        val s = currentStatus ?: return
        if (s.temperature > 42f && (prev?.temperature ?: 0f) <= 42f) {
            svc.speak("Warning! Battery temperature is ${s.temperature} degrees. Consider letting it cool.")
        }

        if (isCharging) { lastAlertLevel = pct; return } // Don't alert while charging

        // Low battery alerts (fire once per threshold)
        when {
            alertAt5 && pct <= 5 && lastAlertLevel > 5 -> {
                svc.speak("Critical! Battery at $pct percent. Please charge immediately.")
                lastAlertLevel = pct
                if (autoLowPowerAt10) enableBatterySaver(context)
            }
            alertAt10 && pct <= 10 && lastAlertLevel > 10 -> {
                svc.speak("Battery very low at $pct percent.")
                lastAlertLevel = pct
                if (autoLowPowerAt10) enableBatterySaver(context)
            }
            alertAt20 && pct <= 20 && lastAlertLevel > 20 -> {
                svc.speak("Battery at $pct percent. Time to plug in.")
                lastAlertLevel = pct
            }
        }
    }

    private fun enableBatterySaver(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            // Note: On Android 5.1+, apps cannot programmatically enable battery saver without system privileges
            // We open settings instead as a fallback
            val intent = Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened battery saver settings")
        } catch (e: Exception) {
            Log.e(TAG, "Battery saver: ${e.message}")
        }
    }
}
