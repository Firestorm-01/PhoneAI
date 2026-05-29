package com.phoneai.assistant.assistant

import android.util.Log
import com.phoneai.assistant.models.ActionType
import com.phoneai.assistant.models.AssistantIntent
import com.phoneai.assistant.utils.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * GroqNLPEngine
 *
 * Sends voice/text input to Groq LLaMA-3.3-70B and parses
 * structured intent JSON. Uses a strict system prompt to ensure
 * safe, deterministic JSON-only responses.
 */
object GroqNLPEngine {

    private const val TAG = "GroqNLPEngine"
    private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.3-70b-versatile"
    private const val TIMEOUT_MS = 10_000

    private val SYSTEM_PROMPT = """
You are PhoneAI, an Android phone assistant that parses user commands into structured JSON.

RULES:
1. Respond ONLY with valid JSON. No preamble, no explanation, no markdown.
2. Map the user's request to exactly ONE action from this list:
   POWER_OFF, RESTART, SLEEP_SCREEN, WAKE_SCREEN,
   ANSWER_CALL, DECLINE_CALL, MAKE_CALL, END_CALL, MUTE_CALL, UNMUTE_CALL,
   SPEAKER_ON, SPEAKER_OFF, HOLD_CALL, EMERGENCY_CALL,
   SEND_SMS, READ_SMS, READ_LAST_SMS,
   VOLUME_UP, VOLUME_DOWN, VOLUME_SET, MUTE_PHONE, UNMUTE_PHONE, DO_NOT_DISTURB,
   WIFI_ON, WIFI_OFF, BLUETOOTH_ON, BLUETOOTH_OFF, AIRPLANE_ON, AIRPLANE_OFF,
   HOTSPOT_ON, HOTSPOT_OFF, FLASHLIGHT_ON, FLASHLIGHT_OFF,
   BRIGHTNESS_UP, BRIGHTNESS_DOWN, BRIGHTNESS_SET, AUTO_BRIGHTNESS,
   SET_ALARM, CANCEL_ALARM, SET_TIMER,
   OPEN_APP, GO_HOME, GO_BACK, TAKE_SCREENSHOT, SHOW_NOTIFS,
   QUERY, UNKNOWN

3. JSON schema:
{
  "action": "ACTION_TYPE",
  "parameters": {
    "contact": "name or number (for calls/SMS)",
    "message": "SMS message body",
    "app_name": "app name to open",
    "volume": "0-100",
    "brightness": "0-100",
    "hour": "0-23",
    "minute": "0-59",
    "duration_seconds": "timer duration",
    "query_text": "for QUERY actions"
  },
  "confidence": 0.0-1.0,
  "response_text": "Brief spoken response to user (max 15 words)"
}

4. Include only relevant parameters. Omit empty/null ones.
5. For QUERY, put the full question in query_text.
6. Always set response_text — it's what will be spoken aloud.
    """.trimIndent()

    /**
     * Parse user input text into a structured AssistantIntent.
     * Returns null on network failure or parse error.
     */
    suspend fun parseIntent(userText: String, conversationContext: String = ""): AssistantIntent? {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = PrefsManager.getConfig().groqApiKey
                if (apiKey.isBlank()) {
                    Log.e(TAG, "No Groq API key configured")
                    return@withContext null
                }

                val messages = buildList {
                    add(mapOf("role" to "system", "content" to SYSTEM_PROMPT))
                    if (conversationContext.isNotBlank()) {
                        add(mapOf("role" to "user", "content" to conversationContext))
                    }
                    add(mapOf("role" to "user", "content" to userText))
                }

                val requestBody = JSONObject().apply {
                    put("model", MODEL)
                    put("max_tokens", 256)
                    put("temperature", 0.1)  // Low temp = deterministic JSON
                    put("messages", org.json.JSONArray(messages.map { m ->
                        JSONObject().apply {
                            put("role", m["role"])
                            put("content", m["content"])
                        }
                    }))
                }

                val url = URL(GROQ_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    doOutput = true
                }

                OutputStreamWriter(conn.outputStream).use { it.write(requestBody.toString()) }

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    Log.e(TAG, "Groq API error $responseCode: $error")
                    return@withContext null
                }

                val responseText = conn.inputStream.bufferedReader().readText()
                parseGroqResponse(responseText, userText)

            } catch (e: Exception) {
                Log.e(TAG, "Intent parsing failed", e)
                null
            }
        }
    }

    private fun parseGroqResponse(responseText: String, originalInput: String): AssistantIntent? {
        return try {
            val root = JSONObject(responseText)
            val content = root
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()

            val parsed = JSONObject(content)

            val actionStr = parsed.optString("action", "UNKNOWN")
            val action = try {
                ActionType.valueOf(actionStr)
            } catch (e: IllegalArgumentException) {
                ActionType.UNKNOWN
            }

            val params = mutableMapOf<String, String>()
            parsed.optJSONObject("parameters")?.let { p ->
                p.keys().forEach { key -> params[key] = p.getString(key) }
            }

            // Store response_text so TTS can speak it
            parsed.optString("response_text").let { if (it.isNotBlank()) params["response_text"] = it }

            val confidence = parsed.optDouble("confidence", 1.0).toFloat()

            AssistantIntent(
                action = action,
                parameters = params,
                rawText = originalInput,
                confidence = confidence
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Groq response", e)
            // Fallback: return UNKNOWN intent
            AssistantIntent(
                action = ActionType.UNKNOWN,
                rawText = originalInput,
                confidence = 0f
            )
        }
    }

    /**
     * For QUERY intents — get a conversational answer from Groq.
     */
    suspend fun answerQuery(query: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = PrefsManager.getConfig().groqApiKey
                if (apiKey.isBlank()) return@withContext "API key not configured."

                val requestBody = JSONObject().apply {
                    put("model", MODEL)
                    put("max_tokens", 128)
                    put("temperature", 0.7)
                    put("messages", org.json.JSONArray(listOf(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", "You are a helpful phone assistant. Answer concisely in 1-2 sentences. Plain text only.")
                        },
                        JSONObject().apply {
                            put("role", "user")
                            put("content", query)
                        }
                    )))
                }

                val url = URL(GROQ_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer ${apiKey}")
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    doOutput = true
                }

                OutputStreamWriter(conn.outputStream).use { it.write(requestBody.toString()) }

                if (conn.responseCode != 200) return@withContext "Sorry, I couldn't get an answer right now."

                val responseText = conn.inputStream.bufferedReader().readText()
                JSONObject(responseText)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()

            } catch (e: Exception) {
                Log.e(TAG, "Query failed", e)
                "Sorry, something went wrong."
            }
        }
    }
}
