package com.phoneai.assistant.services

import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.phoneai.assistant.models.ActionResult
import com.phoneai.assistant.models.CallState
import com.phoneai.assistant.models.CallStateType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * PhoneAIInCallService
 *
 * Manages active calls:
 * - Answer, decline, end calls
 * - Mute, speaker, hold
 * - Tracks call state for the UI and assistant context
 */
class PhoneAIInCallService : InCallService() {

    companion object {
        const val TAG = "PhoneAIInCallSvc"
        var instance: PhoneAIInCallService? = null

        private val _callState = MutableStateFlow<CallState?>(null)
        val callState: StateFlow<CallState?> = _callState
    }

    private var activeCall: Call? = null

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updateCallState(call, state)
            Log.d(TAG, "Call state changed: ${stateLabel(state)}")
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            updateCallState(call, call.state)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "InCall service started")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _callState.value = null
        Log.d(TAG, "InCall service stopped")
    }

    override fun onCallAdded(call: Call) {
        Log.d(TAG, "Call added: ${call.details.handle}")
        activeCall = call
        call.registerCallback(callCallback)
        updateCallState(call, call.state)

        // Notify PhoneAI service that a call is incoming
        PhoneAIService.instance?.speak(
            "Incoming call from ${getCallerLabel(call)}."
        )
    }

    override fun onCallRemoved(call: Call) {
        Log.d(TAG, "Call removed")
        call.unregisterCallback(callCallback)
        if (activeCall == call) {
            activeCall = null
            _callState.value = null
        }
    }

    // ══════════════════════════════════════════════════════════
    //  CALL ACTIONS
    // ══════════════════════════════════════════════════════════

    fun answerCurrentCall(): ActionResult {
        val call = activeCall ?: return ActionResult.Failed("No incoming call")
        return try {
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
            ActionResult.Success("Call answered.")
        } catch (e: Exception) {
            ActionResult.Failed("Answer failed: ${e.message}")
        }
    }

    fun declineCurrentCall(): ActionResult {
        val call = activeCall ?: return ActionResult.Failed("No incoming call")
        return try {
            call.reject(false, null)
            ActionResult.Success("Call declined.")
        } catch (e: Exception) {
            ActionResult.Failed("Decline failed: ${e.message}")
        }
    }

    fun endCurrentCall(): ActionResult {
        val call = activeCall ?: return ActionResult.Failed("No active call")
        return try {
            call.disconnect()
            ActionResult.Success("Call ended.")
        } catch (e: Exception) {
            ActionResult.Failed("End call failed: ${e.message}")
        }
    }

    fun muteCall(mute: Boolean): ActionResult {
        return try {
            setMuted(mute)
            val state = _callState.value
            _callState.value = state?.copy(isMuted = mute)
            ActionResult.Success(if (mute) "Muted." else "Unmuted.")
        } catch (e: Exception) {
            ActionResult.Failed("Mute failed: ${e.message}")
        }
    }

    fun setSpeaker(on: Boolean): ActionResult {
        return try {
            val route = if (on) android.telecom.CallAudioState.ROUTE_SPEAKER
                        else android.telecom.CallAudioState.ROUTE_EARPIECE
            setAudioRoute(route)
            val state = _callState.value
            _callState.value = state?.copy(isOnSpeaker = on)
            ActionResult.Success(if (on) "Speaker on." else "Speaker off.")
        } catch (e: Exception) {
            ActionResult.Failed("Speaker failed: ${e.message}")
        }
    }

    fun holdCall(): ActionResult {
        val call = activeCall ?: return ActionResult.Failed("No active call")
        return try {
            if (call.state == Call.STATE_ACTIVE) {
                call.hold()
                ActionResult.Success("Call on hold.")
            } else if (call.state == Call.STATE_HOLDING) {
                call.unhold()
                ActionResult.Success("Call resumed.")
            } else {
                ActionResult.Failed("Call is not active")
            }
        } catch (e: Exception) {
            ActionResult.Failed("Hold failed: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════
    //  STATE TRACKING
    // ══════════════════════════════════════════════════════════

    private fun updateCallState(call: Call, state: Int) {
        val handle = call.details.handle?.schemeSpecificPart ?: "Unknown"
        _callState.value = CallState(
            number = handle,
            contactName = null, // resolve via ContactsHelper if needed
            state = when (state) {
                Call.STATE_RINGING    -> CallStateType.RINGING
                Call.STATE_ACTIVE     -> CallStateType.ACTIVE
                Call.STATE_HOLDING    -> CallStateType.HOLDING
                else                  -> CallStateType.DISCONNECTED
            },
            startTime = if (state == Call.STATE_ACTIVE) System.currentTimeMillis() else null
        )
    }

    private fun getCallerLabel(call: Call): String {
        return call.details.handle?.schemeSpecificPart ?: "unknown number"
    }

    private fun stateLabel(state: Int) = when (state) {
        Call.STATE_RINGING     -> "RINGING"
        Call.STATE_ACTIVE      -> "ACTIVE"
        Call.STATE_HOLDING     -> "HOLDING"
        Call.STATE_DISCONNECTED-> "DISCONNECTED"
        Call.STATE_DIALING     -> "DIALING"
        Call.STATE_CONNECTING  -> "CONNECTING"
        else                   -> "UNKNOWN($state)"
    }
}
