package com.phoneai.assistant.services

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.phoneai.assistant.PhoneAIApp
import com.phoneai.assistant.assistant.*
import com.phoneai.assistant.models.ActionResult
import com.phoneai.assistant.models.ActionType
import com.phoneai.assistant.models.AssistantIntent
import com.phoneai.assistant.ui.MainActivity
import com.phoneai.assistant.utils.AuditLogger
import com.phoneai.assistant.utils.PrefsManager
import kotlinx.coroutines.*
import java.util.Locale
import java.util.UUID

/**
 * PhoneAIService  (v2 — full feature build)
 *
 * New in v2:
 *  - Shake gesture: single shake = wake, double shake = SOS
 *  - Face-down = silence call
 *  - Battery Guardian integration
 *  - Focus Mode voice commands
 *  - Smart Routine time checks (every 60s)
 *  - Notification reading
 *  - Conversation memory + pronoun resolution
 *  - Partial speech command display
 *  - Wake word confidence scoring
 */
class PhoneAIService : android.app.Service() {

    companion object {
        const val TAG = "PhoneAIService"
        const val NOTIF_ID       = 1001
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
    private var isListening       = false
    private var ttsReady          = false
    private var isAwaitingConfirm = false
    private var isSpeaking        = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var shakeDetector: ShakeGestureDetector
    private val routineCheckRunnable = object : Runnable {
        override fun run() {
            checkRoutines()
            mainHandler.postDelayed(this, 60_000L)
        }
    }

    // ══════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ══════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        instance = this
        executor = ActionExecutor(this)

        initTts()
        initShakeDetector()
        BatteryGuardian.register(this)
        mainHandler.postDelayed(routineCheckRunnable, 60_000L)

        Log.d(TAG, "PhoneAI Service v2 created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START   -> startForegroundService()
            ACTION_STOP    -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
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
        isListening = false
        speechRecognizer?.destroy()
        tts?.shutdown()
        shakeDetector.stop()
        BatteryGuardian.unregister(this)
        mainHandler.removeCallbacks(routineCheckRunnable)
        serviceScope.cancel()
        Log.d(TAG, "PhoneAI Service destroyed")
    }

    // ══════════════════════════════════════════════════════════
    //  FOREGROUND NOTIFICATION
    // ══════════════════════════════════════════════════════════

    private fun startForegroundService() {
        val config = PrefsManager.getConfig()
        val mainPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, PhoneAIService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, PhoneAIApp.CHANNEL_ASSISTANT)
            .setContentTitle("PhoneAI v2 — Listening")
            .setContentText("Say \"${config.wakeWord}\" · Shake to wake · Face-down to silence")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(mainPi)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(NOTIF_ID, notification)
        startContinuousListening()
        Log.d(TAG, "Foreground service started with wake word: ${config.wakeWord}")
    }

    // ══════════════════════════════════════════════════════════
    //  GESTURE DETECTOR
    // ══════════════════════════════════════════════════════════

    private fun initShakeDetector() {
        shakeDetector = ShakeGestureDetector(
            context = this,
            onShake = {
                Log.d(TAG, "Shake detected — waking assistant")
                if (!isAwaitingConfirm) speak("Ready.")
            },
            onDoubleShake = {
                Log.w(TAG, "DOUBLE SHAKE — SOS triggered")
                speak("Calling emergency services.")
                serviceScope.launch {
                    val intent = AssistantIntent(ActionType.EMERGENCY_CALL, mapOf())
                    executor?.execute(intent)
                }
            },
            onFaceDown = {
                Log.d(TAG, "Face-down — silencing call")
                val inCall = PhoneAIInCallService.instance
                if (inCall != null) {
                    inCall.muteCall(true)
                    speak("Call muted.")
                } else {
                    // Silence ringer
                    val audio = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
                    audio.ringerMode = android.media.AudioManager.RINGER_MODE_VIBRATE
                }
            }
        )
        shakeDetector.start()
    }

    // ══════════════════════════════════════════════════════════
    //  ROUTINE ENGINE
    // ══════════════════════════════════════════════════════════

    private fun checkRoutines() {
        val toRun = SmartRoutineEngine.checkTimeRoutines(this)
        if (toRun.isEmpty()) return

        serviceScope.launch {
            toRun.forEach { routine ->
                Log.d(TAG, "Running routine: ${routine.name}")
                speak("Running ${routine.name} routine.")
                routine.actions.forEach { intent ->
                    val result = executor?.execute(intent)
                    if (result is ActionResult.Success) Log.d(TAG, "Routine action: ${result.message}")
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  SPEECH RECOGNITION
    // ══════════════════════════════════════════════════════════

    private fun startContinuousListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "Speech recognition unavailable")
            return
        }
        isListening = true
        startListenCycle()
    }

    private fun startListenCycle() {
        if (!isListening) return
        if (isSpeaking) {
            // Don't listen while TTS is speaking — wait
            mainHandler.postDelayed({ startListenCycle() }, 500)
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEvent(t: Int, p: android.os.Bundle?) {}

            override fun onPartialResults(partial: android.os.Bundle?) {
                // Show partial results in notification for responsiveness
                val partialText = partial
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                Log.v(TAG, "Partial: $partialText")
            }

            override fun onResults(results: android.os.Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
                Log.d(TAG, "ASR result: \"$text\"")
                if (text.isNotBlank()) onSpeechResult(text)
                serviceScope.launch {
                    delay(300)
                    if (isListening) startListenCycle()
                }
            }

            override fun onError(error: Int) {
                // Error 7 (NO_MATCH) and 6 (SPEECH_TIMEOUT) are normal — just restart
                val wait = if (error in listOf(7, 6)) 200L else 1000L
                serviceScope.launch {
                    delay(wait)
                    if (isListening) startListenCycle()
                }
            }

            override fun onEndOfSpeech() {}
        })

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed: ${e.message}")
        }
    }

    private fun onSpeechResult(text: String) {
        val lower = text.lowercase().trim()
        val config = PrefsManager.getConfig()
        val wakeWord = config.wakeWord.lowercase()

        // ── Confirmation flow ──
        if (isAwaitingConfirm) {
            when {
                lower.contains("confirm") || lower.contains("yes") ||
                lower.contains("do it")  || lower.contains("proceed") -> {
                    isAwaitingConfirm = false
                    SafetyGate.confirm()
                }
                lower.contains("cancel") || lower.contains("no") ||
                lower.contains("stop")   || lower.contains("abort") -> {
                    isAwaitingConfirm = false
                    SafetyGate.cancel()
                    speak("Cancelled.")
                }
            }
            return
        }

        // ── Wake word extraction ──
        val wakeIndex = lower.indexOf(wakeWord)
        if (wakeIndex == -1) return   // Not addressed to us

        val command = lower.substring(wakeIndex + wakeWord.length).trim()
        if (command.isBlank()) { speak("Yes?"); return }

        processCommand(command)
    }

    // ══════════════════════════════════════════════════════════
    //  COMMAND PIPELINE
    // ══════════════════════════════════════════════════════════

    fun processCommand(rawText: String) {
        serviceScope.launch {
            // Step 1: Pronoun resolution from conversation memory
            val resolvedText = ConversationMemory.resolvePronouns(rawText)
            Log.d(TAG, "Processing: \"$resolvedText\"")

            // Step 2: Check for built-in shortcuts (no NLP needed, faster)
            val shortcutResult = tryShortcut(resolvedText)
            if (shortcutResult != null) {
                handleResult(shortcutResult.first, shortcutResult.second)
                return@launch
            }

            // Step 3: Groq NLP with conversation context
            val context = ConversationMemory.buildContextSummary()
            val intent  = GroqNLPEngine.parseIntent(resolvedText, context)

            if (intent == null) {
                speak("Sorry, I couldn't connect to parse that. Check your API key.")
                return@launch
            }

            // Speak interim response if available and not a query
            val responseText = intent.parameters["response_text"]
            if (!responseText.isNullOrBlank() && intent.action != ActionType.QUERY) {
                speak(responseText)
            }

            // Step 4: Safety gate
            SafetyGate.evaluate(
                context = this@PhoneAIService,
                intent  = intent,
                onProceed = {
                    serviceScope.launch {
                        val result = executor!!.execute(intent)
                        ConversationMemory.recordTurn(resolvedText, intent.action.name, intent.parameters,
                            when (result) {
                                is ActionResult.Success -> result.message
                                is ActionResult.Failed  -> "FAILED"
                                else -> "OTHER"
                            })
                        handleResult(intent, result)
                    }
                },
                onBlocked = { reason ->
                    speak(reason)
                    AuditLogger.logBlocked(intent, reason)
                }
            )

            if (SafetyGate.pendingAction.value != null) isAwaitingConfirm = true
        }
    }

    /**
     * Ultra-fast shortcuts that bypass NLP for common single-word commands.
     * Returns (intent, result) pair or null if not matched.
     */
    private suspend fun tryShortcut(text: String): Pair<AssistantIntent, ActionResult>? {
        val lower = text.lowercase().trim()
        return when {
            lower == "battery" || lower == "battery level" -> {
                val response = BatteryGuardian.buildSpeechResponse(this)
                val intent = AssistantIntent(ActionType.QUERY, mapOf("response_text" to response))
                Pair(intent, ActionResult.Success(response))
            }
            lower == "notifications" || lower == "read notifications" -> {
                val summary = PhoneAINotificationService.instance?.getNotificationSummary()
                    ?: "Notification service not enabled."
                val intent = AssistantIntent(ActionType.SHOW_NOTIFS, mapOf())
                Pair(intent, ActionResult.Success(summary))
            }
            lower.contains("focus mode") || lower.contains("sleep mode") ||
            lower.contains("drive mode") || lower.contains("gym mode") ||
            lower.contains("meeting mode") -> {
                val parsed = FocusModeManager.parseFromCommand(lower)
                if (parsed != null) {
                    val result = FocusModeManager.activate(this, parsed.first, parsed.second)
                    val intent = AssistantIntent(ActionType.DO_NOT_DISTURB, mapOf())
                    Pair(intent, result)
                } else null
            }
            lower == "dismiss notifications" || lower == "clear notifications" -> {
                PhoneAINotificationService.instance?.dismissAll()
                val intent = AssistantIntent(ActionType.SHOW_NOTIFS, mapOf())
                Pair(intent, ActionResult.Success("All notifications cleared."))
            }
            else -> null
        }
    }

    private fun handleResult(intent: AssistantIntent, result: ActionResult) {
        when (result) {
            is ActionResult.Success -> speak(result.message)
            is ActionResult.Failed  -> speak("Couldn't do that. ${result.reason}")
            is ActionResult.Blocked -> speak(result.reason)
            is ActionResult.NeedsConfirmation -> {}
        }
    }

    // ══════════════════════════════════════════════════════════
    //  TTS
    // ══════════════════════════════════════════════════════════

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(PrefsManager.getConfig().ttsSpeed)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?)  { isSpeaking = true }
                    override fun onDone(id: String?)   { isSpeaking = false }
                    @Deprecated("Deprecated")
                    override fun onError(id: String?)  { isSpeaking = false }
                })
                ttsReady = true
                Log.d(TAG, "TTS ready")
            } else {
                Log.e(TAG, "TTS init failed: $status")
            }
        }
    }

    fun speak(text: String) {
        if (!PrefsManager.getConfig().ttsEnabled) return
        if (!ttsReady) { Log.w(TAG, "TTS not ready: $text"); return }
        Log.d(TAG, "TTS: $text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun speakQueued(text: String) {
        if (!PrefsManager.getConfig().ttsEnabled) return
        if (!ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }
}
