package com.phoneai.assistant.assistant

import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.util.Log
import com.phoneai.assistant.models.ActionResult
import com.phoneai.assistant.models.ActionType
import com.phoneai.assistant.models.AssistantIntent
import com.phoneai.assistant.models.DangerLevel
import com.phoneai.assistant.ui.ConfirmationActivity
import com.phoneai.assistant.utils.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * SafetyGate
 *
 * Central safety layer. Every action passes through here.
 * - Blocks dangerous actions without confirmation
 * - Emergency bypass for 911/112
 * - Timeout auto-cancel
 * - Rate limiting to prevent abuse
 */
object SafetyGate {

    private const val TAG = "SafetyGate"
    private const val RATE_LIMIT_WINDOW_MS = 60_000L
    private const val MAX_ACTIONS_PER_MINUTE = 20

    data class PendingAction(
        val intent: AssistantIntent,
        val onConfirm: () -> Unit,
        val onCancel: () -> Unit,
        val expiresAt: Long
    )

    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    val pendingAction: StateFlow<PendingAction?> = _pendingAction

    private var confirmationTimer: CountDownTimer? = null
    private val actionTimestamps = ArrayDeque<Long>()

    /**
     * Main entry point. Returns whether the action should proceed immediately,
     * needs confirmation, or is blocked.
     */
    fun evaluate(
        context: Context,
        intent: AssistantIntent,
        onProceed: () -> Unit,
        onBlocked: (String) -> Unit
    ) {
        val action = intent.action

        // ── 1. Emergency bypass — always allow ──
        if (action == ActionType.EMERGENCY_CALL) {
            Log.w(TAG, "EMERGENCY ACTION — bypassing all gates")
            onProceed()
            return
        }

        // ── 2. Rate limiting ──
        if (isRateLimited()) {
            onBlocked("Too many commands. Please slow down.")
            return
        }
        recordAction()

        // ── 3. Check confidence threshold ──
        if (intent.confidence < 0.5f) {
            onBlocked("I'm not sure what you want. Please try again.")
            return
        }

        // ── 4. Actions that always proceed immediately ──
        if (action.dangerLevel == DangerLevel.NONE) {
            onProceed()
            return
        }

        // ── 5. Actions requiring confirmation ──
        if (action.requiresConfirmation) {
            val confirmMessage = buildConfirmationMessage(intent)
            requestConfirmation(
                context = context,
                intent = intent,
                message = confirmMessage,
                onConfirm = onProceed,
                onCancel = { onBlocked("Action cancelled.") }
            )
            return
        }

        // ── 6. LOW/MEDIUM danger — proceed with audit ──
        onProceed()
    }

    private fun requestConfirmation(
        context: Context,
        intent: AssistantIntent,
        message: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        val config = PrefsManager.getConfig()
        val timeout = config.confirmationTimeoutMs

        val pending = PendingAction(
            intent = intent,
            onConfirm = {
                cancelTimer()
                _pendingAction.value = null
                onConfirm()
            },
            onCancel = {
                cancelTimer()
                _pendingAction.value = null
                onCancel()
            },
            expiresAt = System.currentTimeMillis() + timeout
        )
        _pendingAction.value = pending

        // Start timeout timer
        confirmationTimer = object : CountDownTimer(timeout, 1000) {
            override fun onTick(ms: Long) {}
            override fun onFinish() {
                Log.d(TAG, "Confirmation timed out — cancelling ${intent.action}")
                pending.onCancel()
            }
        }.start()

        // Launch confirmation activity (works on lock screen)
        val confirmIntent = Intent(context, ConfirmationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("message", message)
            putExtra("action", intent.action.name)
            putExtra("timeout_ms", timeout)
        }
        context.startActivity(confirmIntent)
    }

    /**
     * Called by ConfirmationActivity when user says "yes" or "confirm".
     */
    fun confirm() {
        _pendingAction.value?.onConfirm?.invoke()
    }

    /**
     * Called by ConfirmationActivity when user says "no" or "cancel".
     */
    fun cancel() {
        _pendingAction.value?.onCancel?.invoke()
    }

    private fun cancelTimer() {
        confirmationTimer?.cancel()
        confirmationTimer = null
    }

    private fun buildConfirmationMessage(intent: AssistantIntent): String {
        val params = intent.parameters
        return when (intent.action) {
            ActionType.POWER_OFF   -> "Are you sure you want to power off the phone?"
            ActionType.RESTART     -> "Are you sure you want to restart the phone?"
            ActionType.MAKE_CALL   -> {
                val contact = params["contact"] ?: "this number"
                "Should I call $contact?"
            }
            ActionType.SEND_SMS    -> {
                val contact = params["contact"] ?: "this contact"
                val msg = params["message"] ?: ""
                "Send '$msg' to $contact?"
            }
            ActionType.AIRPLANE_ON -> "Enable airplane mode? This will cut all connections."
            else                   -> "Confirm: ${intent.action.label}?"
        }
    }

    private fun isRateLimited(): Boolean {
        val now = System.currentTimeMillis()
        val cutoff = now - RATE_LIMIT_WINDOW_MS
        while (actionTimestamps.isNotEmpty() && actionTimestamps.first() < cutoff) {
            actionTimestamps.removeFirst()
        }
        return actionTimestamps.size >= MAX_ACTIONS_PER_MINUTE
    }

    private fun recordAction() {
        actionTimestamps.addLast(System.currentTimeMillis())
    }
}
