package com.phoneai.assistant.services

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.phoneai.assistant.utils.PrefsManager

/**
 * PhoneAICallScreeningService
 *
 * Screens incoming calls BEFORE they ring:
 * - Auto-answers whitelisted numbers (if enabled)
 * - Auto-declines known spam patterns
 * - Silences unknown numbers (optional)
 * - Lets PhoneAI service announce caller
 */
class PhoneAICallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "CallScreening"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: ""
        val config = PrefsManager.getConfig()

        Log.d(TAG, "Screening call from: $number")

        val response = CallResponse.Builder()

        when {
            // ── Emergency always allow ──
            isEmergencyNumber(number, config) -> {
                Log.d(TAG, "Emergency number — allowing")
                response
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSkipNotification(false)
                    .setSilenceCall(false)
            }

            // ── Whitelist auto-answer ──
            config.autoAnswerEnabled && isWhitelisted(number, config) -> {
                Log.d(TAG, "Whitelisted — allowing silently for auto-answer")
                response
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSilenceCall(false)
                    .setSkipNotification(false)
            }

            // ── Spam block ──
            config.spamBlockEnabled && isSpam(number) -> {
                Log.d(TAG, "Spam detected — rejecting $number")
                response
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)
            }

            // ── Unknown number (silent ring option) ──
            else -> {
                Log.d(TAG, "Normal call — allowing")
                response
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSilenceCall(false)
                    .setSkipNotification(false)
            }
        }

        respondToCall(callDetails, response.build())
    }

    private fun isEmergencyNumber(number: String, config: com.phoneai.assistant.models.AssistantConfig): Boolean {
        return config.emergencyNumbers.any { number.contains(it) }
    }

    private fun isWhitelisted(number: String, config: com.phoneai.assistant.models.AssistantConfig): Boolean {
        return config.autoAnswerWhitelist.any { whitelist ->
            number.contains(whitelist) || whitelist.contains(number)
        }
    }

    private fun isSpam(number: String): Boolean {
        // Heuristics for spam detection
        // In production: integrate with spam DB API
        return when {
            number.length < 5 -> false  // Short codes are often legitimate
            number.startsWith("+1900") -> true  // Premium rate numbers
            number.startsWith("00000") -> true  // Clearly fake
            KNOWN_SPAM_PATTERNS.any { number.contains(it) } -> true
            else -> false
        }
    }

    companion object {
        // Add known spam area codes/patterns here
        private val KNOWN_SPAM_PATTERNS = listOf(
            "18005551234" // Example placeholder
        )
    }
}
