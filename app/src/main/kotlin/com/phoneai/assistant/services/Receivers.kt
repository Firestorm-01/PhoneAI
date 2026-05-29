// ══════════════════════════════════════════════════════════
//  BootReceiver.kt  — Auto-restart service on boot
// ══════════════════════════════════════════════════════════
package com.phoneai.assistant.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Boot completed — starting PhoneAI")
            val serviceIntent = Intent(context, PhoneAIService::class.java).apply {
                action = PhoneAIService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}


// ══════════════════════════════════════════════════════════
//  SmsReceiver.kt  — Read incoming SMS, optionally respond
// ══════════════════════════════════════════════════════════
package com.phoneai.assistant.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.phoneai.assistant.utils.ContactsHelper
import com.phoneai.assistant.utils.PrefsManager

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender  = messages[0].displayOriginatingAddress ?: "Unknown"
        val body    = messages.joinToString("") { it.displayMessageBody }
        val config  = PrefsManager.getConfig()
        val name    = ContactsHelper.findName(context, sender) ?: sender

        Log.d("SmsReceiver", "SMS from $name: $body")

        // Announce via TTS if assistant is running
        PhoneAIService.instance?.speak("New message from $name.")
    }
}


// ══════════════════════════════════════════════════════════
//  PhoneAIDeviceAdminReceiver.kt
// ══════════════════════════════════════════════════════════
package com.phoneai.assistant.services

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PhoneAIDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Log.d("DeviceAdmin", "Device admin enabled")
    }
    override fun onDisabled(context: Context, intent: Intent) {
        Log.d("DeviceAdmin", "Device admin disabled")
    }
}
