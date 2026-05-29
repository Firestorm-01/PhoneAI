package com.phoneai.assistant.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.phoneai.assistant.PhoneAIApp
import com.phoneai.assistant.assistant.ActionExecutor
import com.phoneai.assistant.assistant.GroqNLPEngine
import com.phoneai.assistant.assistant.SafetyGate
import com.phoneai.assistant.models.ActionResult
import com.phoneai.assistant.models.ActionType
import com.phoneai.assistant.ui.MainActivity
import com.phoneai.assistant.utils.AuditLogger
import com.phoneai.assistant.utils.PrefsManager
import kotlinx.coroutines.*
import java.util.Locale
import java.util.UUID

/**
 * PhoneAIService
 *
 * Foreground service that:
 * - Keeps the assistant alive in the background
 * - Listens for wake word (via continuous speech recognition)
 * - Coordinates NLP → SafetyGate → ActionExecutor → TTS pipeline
 * - Handles voice confirmation ("confirm" / "cancel") for dangerous actions
 */
class PhoneAIService : Service() {

    companion object {
        const val TAG = "PhoneAIService"
        const val NOTIF_ID = 1001
        const val ACTION_START   = "START"
        const val ACTION_STOP    = "STOP"
        const val ACTION_COMMAND = "COMMAND"
        const val EXTRA_COMMAND  = "command_text"

        var instance: PhoneAIService? = null
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var executor: ActionExecutor? = null
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isAwaitingConfirmation = false
    private var ttsReady = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        executor = ActionExecutor(this)
        initTts()
        Log.d(TAG, "PhoneAI Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START   -> startForegroundService()
            ACTION_STOP    -> stopSelf()
            ACTION_COMMAND -> {
                val cmd = intent.getStringExtra(EXTRA_COMMAND) ?: return START_STICKY
                processCommand(cmd)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopListening()
        tts?.shutdown()
        serviceScope.cancel()
        Log.d(TAG, "PhoneAI Service destroyed")
    }

    // ══════════════════════════════════════════════════════════
    //  FOREGROUND NOTIFICATION
    // ══════════════════════════════════════════════════════════

    private fun startForegroundService() {
        val mainIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PhoneAIService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, PhoneAIApp.CHANNEL_ASSISTANT)
            .setContentTitle("PhoneAI Active")
            .setContentText("Say \"Hey Phone\" to wake me")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(mainIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(NOTIF_ID, notification)
        startContinuousListening()
    }

    // ══════════════════════════════════════════════════════════
    //  SPEECH RECOGNITION
    // ══════════════════════════════════════════════════════════

    private fun startContinuousListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "Speech recognition not available")
            return
        }
        isListening = true
        startListeningCycle()
    }

    private fun startListeningCycle() {
        if (!isListening) return

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                Log.d(TAG, "Heard: $text")
                onSpeechResult(text)
                // Restart cycle after brief delay
                serviceScope.launch {
                    delay(500)
                    if (isListening) startListeningCycle()
                }
            }

            override fun onError(error: Int) {
                Log.d(TAG, "Speech error: $error")
                serviceScope.launch {
                    delay(1000)
                    if (isListening) startListeningCycle()
                }
            }

            override fun onEndOfSpeech() {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun onSpeechResult(text: String) {
        if (text.isBlank()) return

        val wakeWord = PrefsManager.getConfig().wakeWord.lowercase()

        // ── Confirmation flow ──
        if (isAwaitingConfirmation) {
            val lower = text.lowercase()
            when {
                lower.contains("confirm") || lower.contains("yes") || lower.contains("do it") -> {
                    isAwaitingConfirmation = false
                    SafetyGate.confirm()
                }
                lower.contains("cancel") || lower.contains("no") || lower.contains("stop") -> {
                    isAwaitingConfirmation = false
                    SafetyGate.cancel()
                    speak("Action cancelled.")
                }
            }
            return
        }

        // ── Wake word detection ──
        val lower = text.lowercase()
        if (lower.contains(wakeWord)) {
            val command = lower.substringAfter(wakeWord).trim()
            if (command.isNotBlank()) {
                processCommand(command)
            } else {
                speak("Ready.")
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  COMMAND PROCESSING PIPELINE
    // ══════════════════════════════════════════════════════════

    fun processCommand(text: String) {
        serviceScope.launch {
            Log.d(TAG, "Processing: $text")

            // Parse intent
            val intent = GroqNLPEngine.parseIntent(text)
            if (intent == null) {
                speak("Sorry, I couldn't understand that.")
                return@launch
            }

            // Speak interim response
            val responseText = intent.parameters["response_text"]
            if (!responseText.isNullOrBlank() && intent.action != ActionType.QUERY) {
                speak(responseText)
            }

            // Safety gate
            SafetyGate.evaluate(
                context = this@PhoneAIService,
                intent = intent,
                onProceed = {
                    serviceScope.launch {
                        val result = executor!!.execute(intent)
                        handleResult(result)
                    }
                },
                onBlocked = { reason ->
                    speak(reason)
                    AuditLogger.logBlocked(intent, reason)
                }
            )

            // If needs confirmation, start listening for "confirm"/"cancel"
            if (SafetyGate.pendingAction.value != null) {
                isAwaitingConfirmation = true
            }
        }
    }

    private fun handleResult(result: ActionResult) {
        when (result) {
            is ActionResult.Success -> speak(result.message)
            is ActionResult.Failed  -> speak("Failed: ${result.reason}")
            is ActionResult.Blocked -> speak(result.reason)
            is ActionResult.NeedsConfirmation -> {}
        }
    }

    // ══════════════════════════════════════════════════════════
    //  TEXT TO SPEECH
    // ══════════════════════════════════════════════════════════

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(PrefsManager.getConfig().ttsSpeed)
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    @Deprecated("Deprecated")
                    override fun onError(utteranceId: String?) {}
                })
                Log.d(TAG, "TTS ready")
            } else {
                Log.e(TAG, "TTS init failed: $status")
            }
        }
    }

    fun speak(text: String) {
        if (!PrefsManager.getConfig().ttsEnabled) return
        if (!ttsReady) { Log.w(TAG, "TTS not ready, dropping: $text"); return }

        Log.d(TAG, "Speaking: $text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }
}
