package com.phoneai.assistant.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.phoneai.assistant.models.ActionResult

/**
 * PhoneAIAccessibilityService
 *
 * Provides deep system control:
 * - Power menu (power off / restart)
 * - Screen lock
 * - Global navigation (home, back, recents, notifications, screenshots)
 * - UI event monitoring for context awareness
 */
class PhoneAIAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "PhoneAIAccessSvc"
        var instance: PhoneAIAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
        serviceInfo = serviceInfo.apply {
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
        }
        Log.d(TAG, "Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitor events for context (active app, screen state, etc.)
        // Can be used to provide context-aware suggestions
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // ══════════════════════════════════════════════════════════
    //  POWER CONTROL
    // ══════════════════════════════════════════════════════════

    /**
     * Shows power menu (power off / restart / etc.)
     * Works without root — uses performGlobalAction.
     */
    fun showPowerMenu(): ActionResult {
        return if (performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)) {
            ActionResult.Success("Power menu opened.")
        } else {
            ActionResult.Failed("Could not open power menu.")
        }
    }

    /**
     * Lock screen immediately.
     */
    fun lockScreen(): ActionResult {
        return if (performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)) {
            ActionResult.Success("Screen locked.")
        } else {
            ActionResult.Failed("Screen lock failed. Ensure accessibility service is active.")
        }
    }

    // ══════════════════════════════════════════════════════════
    //  NAVIGATION
    // ══════════════════════════════════════════════════════════

    fun goHome(): ActionResult {
        return if (performGlobalAction(GLOBAL_ACTION_HOME)) {
            ActionResult.Success("Going home.")
        } else {
            ActionResult.Failed("Home navigation failed.")
        }
    }

    fun goBack(): ActionResult {
        return if (performGlobalAction(GLOBAL_ACTION_BACK)) {
            ActionResult.Success("Going back.")
        } else {
            ActionResult.Failed("Back navigation failed.")
        }
    }

    fun showRecents(): ActionResult {
        return if (performGlobalAction(GLOBAL_ACTION_RECENTS)) {
            ActionResult.Success("Opening recent apps.")
        } else {
            ActionResult.Failed("Recents failed.")
        }
    }

    fun showNotifications(): ActionResult {
        return if (performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)) {
            ActionResult.Success("Opening notifications.")
        } else {
            ActionResult.Failed("Notifications failed.")
        }
    }

    fun takeScreenshot(): ActionResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)) {
                ActionResult.Success("Screenshot taken.")
            } else {
                ActionResult.Failed("Screenshot failed.")
            }
        } else {
            ActionResult.Failed("Screenshots require Android 9+.")
        }
    }

    fun showQuickSettings(): ActionResult {
        return if (performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)) {
            ActionResult.Success("Opening quick settings.")
        } else {
            ActionResult.Failed("Quick settings failed.")
        }
    }
}
