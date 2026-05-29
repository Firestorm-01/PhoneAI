package com.phoneai.assistant.assistant

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * ShakeGestureDetector
 *
 * Hardware gesture detection for hands-free activation:
 *
 * SHAKE          → Wake assistant (alternative to wake word, works in pocket)
 * DOUBLE_SHAKE   → Emergency SOS (call 112)
 * HOLD_FACE_DOWN → Auto-silence incoming call
 *
 * Calibrated with hysteresis to avoid false positives.
 * Uses accelerometer + gravity correction (accounts for device orientation).
 */
class ShakeGestureDetector(
    private val context: Context,
    private val onShake: () -> Unit,
    private val onDoubleShake: () -> Unit,
    private val onFaceDown: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "ShakeDetector"

        // Shake threshold — m/s² above gravity
        private const val SHAKE_THRESHOLD_GRAVITY = 2.7f
        private const val SHAKE_SLOP_TIME_MS = 500L
        private const val SHAKE_COUNT_RESET_TIME_MS = 3000L
        private const val DOUBLE_SHAKE_COUNT = 2

        // Face-down threshold: Z-axis < -8 m/s² (screen toward floor)
        private const val FACE_DOWN_THRESHOLD = -8.0f
        private const val FACE_DOWN_HOLD_MS = 1200L
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravitySensor  = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private var shakeCount = 0
    private var lastShakeTime = 0L
    private var shakeCountResetTime = 0L
    private var lastFaceDownTime = 0L
    private var faceDownHandled = false
    private var gravity = FloatArray(3) { 0f }
    private var registered = false

    fun start() {
        if (registered) return
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gravitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        registered = true
        Log.d(TAG, "ShakeDetector started")
    }

    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                gravity = event.values.clone()
            }

            Sensor.TYPE_ACCELEROMETER -> {
                // Remove gravity component
                val ax = event.values[0] - gravity[0]
                val ay = event.values[1] - gravity[1]
                val az = event.values[2] - gravity[2]

                val gForce = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat() / SensorManager.GRAVITY_EARTH

                val now = System.currentTimeMillis()

                // ── Shake detection ──
                if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                    if (now - lastShakeTime > SHAKE_SLOP_TIME_MS) {
                        lastShakeTime = now

                        if (now - shakeCountResetTime > SHAKE_COUNT_RESET_TIME_MS) {
                            shakeCount = 0
                            shakeCountResetTime = now
                        }

                        shakeCount++
                        Log.d(TAG, "Shake #$shakeCount (gForce=$gForce)")

                        when (shakeCount) {
                            1 -> onShake()
                            DOUBLE_SHAKE_COUNT -> {
                                onDoubleShake()
                                shakeCount = 0
                            }
                        }
                    }
                }

                // ── Face-down detection ──
                // az heavily negative when screen faces floor
                if (az < FACE_DOWN_THRESHOLD) {
                    if (lastFaceDownTime == 0L) lastFaceDownTime = now
                    val heldMs = now - lastFaceDownTime
                    if (heldMs >= FACE_DOWN_HOLD_MS && !faceDownHandled) {
                        faceDownHandled = true
                        Log.d(TAG, "Face-down held for ${heldMs}ms")
                        onFaceDown()
                    }
                } else {
                    lastFaceDownTime = 0L
                    faceDownHandled = false
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
