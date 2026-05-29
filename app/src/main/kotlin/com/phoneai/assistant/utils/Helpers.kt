// ══════════════════════════════════════════════════════════
//  AuditLogger.kt
// ══════════════════════════════════════════════════════════
package com.phoneai.assistant.utils

import android.content.Context
import android.util.Log
import com.phoneai.assistant.models.ActionResult
import com.phoneai.assistant.models.AssistantIntent
import com.phoneai.assistant.models.AuditEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AuditLogger {
    private const val TAG = "AuditLogger"
    private const val MAX_ENTRIES = 500
    private lateinit var logFile: File

    fun init(context: Context) {
        logFile = File(context.filesDir, "audit_log.json")
        if (!logFile.exists()) logFile.writeText("[]")
    }

    fun log(intent: AssistantIntent, result: ActionResult) {
        val entry = AuditEntry(
            timestamp  = System.currentTimeMillis(),
            action     = intent.action.name,
            parameters = intent.parameters.toString(),
            result     = when (result) {
                is ActionResult.Success -> "SUCCESS: ${result.message}"
                is ActionResult.Failed  -> "FAILED: ${result.reason}"
                is ActionResult.Blocked -> "BLOCKED: ${result.reason}"
                is ActionResult.NeedsConfirmation -> "PENDING_CONFIRM"
            },
            confirmed  = result is ActionResult.Success
        )
        appendEntry(entry)
        Log.d(TAG, "[AUDIT] ${entry.action} → ${entry.result}")
    }

    fun logBlocked(intent: AssistantIntent, reason: String) {
        val entry = AuditEntry(
            timestamp  = System.currentTimeMillis(),
            action     = intent.action.name,
            parameters = intent.parameters.toString(),
            result     = "BLOCKED: $reason",
            confirmed  = false
        )
        appendEntry(entry)
    }

    fun getRecentEntries(limit: Int = 50): List<AuditEntry> {
        return try {
            val array = JSONArray(logFile.readText())
            (0 until minOf(limit, array.length())).map { i ->
                val obj = array.getJSONObject(i)
                AuditEntry(
                    timestamp  = obj.getLong("timestamp"),
                    action     = obj.getString("action"),
                    parameters = obj.getString("parameters"),
                    result     = obj.getString("result"),
                    confirmed  = obj.getBoolean("confirmed")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearLog() {
        logFile.writeText("[]")
    }

    private fun appendEntry(entry: AuditEntry) {
        try {
            val array = JSONArray(logFile.readText())
            val obj = JSONObject().apply {
                put("timestamp",  entry.timestamp)
                put("action",     entry.action)
                put("parameters", entry.parameters)
                put("result",     entry.result)
                put("confirmed",  entry.confirmed)
            }
            // Prepend newest first
            val newArray = JSONArray().apply {
                put(obj)
                for (i in 0 until minOf(array.length(), MAX_ENTRIES - 1)) {
                    put(array.getJSONObject(i))
                }
            }
            logFile.writeText(newArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Audit log write failed", e)
        }
    }
}


// ══════════════════════════════════════════════════════════
//  ContactsHelper.kt
// ══════════════════════════════════════════════════════════
package com.phoneai.assistant.utils

import android.content.Context
import android.provider.ContactsContract
import android.util.Log

object ContactsHelper {
    private const val TAG = "ContactsHelper"

    fun findNumber(context: Context, name: String): String? {
        return try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                while (it.moveToNext()) {
                    val contactName = it.getString(0) ?: continue
                    val number      = it.getString(1) ?: continue
                    if (contactName.lowercase().contains(name.lowercase())) {
                        return number.replace(" ", "").replace("-", "")
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Contact lookup failed", e)
            null
        }
    }

    fun findName(context: Context, number: String): String? {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
