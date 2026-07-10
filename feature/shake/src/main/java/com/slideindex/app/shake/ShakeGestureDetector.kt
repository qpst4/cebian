package com.slideindex.app.shake

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Shake detection aligned with com.ksxkq.floating.runtime.ShakeController (8.0.8):
 * gyroscope angular velocity thresholds per axis, 500ms cooldown, reverse-direction guard.
 */
class ShakeGestureDetector(
    context: Context,
    private val onGestureDetected: (ShakeGestureType) -> Unit,
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val sensorHandler = Handler(Looper.getMainLooper())

    val isAvailable: Boolean get() = gyroscope != null || accelerometer != null

    private var activeSensor: Sensor? = null
    private var useAccelerometer = false
    private var globalSensitivity = 6.0f
    private var independentEnabled = false
    private var perDirectionSensitivity: Map<ShakeGestureType, Float> = emptyMap()
    private var listenerRegistered = false

    private var lastTriggerTime = 0L
    private var lastDirection: ShakeGestureType? = null
    private var lastAccel = floatArrayOf(0f, 0f, 0f)
    private var accelReady = false

    fun setSensitivity(
        global: Float,
        independentEnabled: Boolean,
        perDirection: Map<ShakeGestureType, Float>,
    ) {
        globalSensitivity = global.coerceIn(1f, 10f)
        this.independentEnabled = independentEnabled
        perDirectionSensitivity = perDirection
    }

    fun start() {
        stop()
        useAccelerometer = gyroscope == null && accelerometer != null
        accelReady = false
        val sensor = gyroscope ?: accelerometer
        activeSensor = sensor
        if (sensor == null) {
            Log.w(TAG, "start: no gyroscope or accelerometer on device")
            return
        }
        val registered = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_GAME,
            sensorHandler,
        )
        listenerRegistered = registered
        Log.d(
            TAG,
            "start: sensor=${sensor.name} type=${sensor.type} accelFallback=$useAccelerometer registered=$registered",
        )
    }

    fun stop() {
        if (listenerRegistered) {
            sensorManager.unregisterListener(this)
            listenerRegistered = false
        }
        activeSensor = null
        lastTriggerTime = 0L
        lastDirection = null
        accelReady = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> handleGyroscope(event)
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun handleGyroscope(event: SensorEvent) {
        val gx = roundAxis(event.values[0])
        val gy = roundAxis(event.values[1])
        val gz = roundAxis(event.values[2])
        val direction = detectDirection(gx, gy, gz, abs(gx), abs(gy), abs(gz)) ?: return
        emitGesture(direction)
    }

    private fun handleAccelerometer(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        if (!accelReady) {
            lastAccel[0] = ax
            lastAccel[1] = ay
            lastAccel[2] = az
            accelReady = true
            return
        }

        val dx = ax - lastAccel[0]
        val dy = ay - lastAccel[1]
        val dz = az - lastAccel[2]
        lastAccel[0] = ax
        lastAccel[1] = ay
        lastAccel[2] = az

        val magnitude = sqrt(dx * dx + dy * dy + dz * dz)
        val threshold = effectiveThreshold(globalSensitivity) * ACCEL_DELTA_SCALE
        if (magnitude <= threshold) return

        val direction = detectDirection(
            dx,
            dy,
            dz,
            abs(dx),
            abs(dy),
            abs(dz),
            axisScale = 1f,
        ) ?: return
        emitGesture(direction)
    }

    private fun emitGesture(direction: ShakeGestureType) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < NORMAL_COOLDOWN_MS) return

        if (lastDirection != null && isReverseDirection(direction, lastDirection!!)) {
            if (now - lastTriggerTime < REVERSE_IGNORE_MS) return
        }

        lastTriggerTime = now
        lastDirection = direction
        Log.d(TAG, "gesture detected: $direction (accel=$useAccelerometer)")
        onGestureDetected(direction)
    }

    private fun detectDirection(
        axisX: Float,
        axisY: Float,
        axisZ: Float,
        absX: Float,
        absY: Float,
        absZ: Float,
        axisScale: Float = 1f,
    ): ShakeGestureType? {
        if (independentEnabled) {
            return detectPerDirection(axisX, axisY, axisZ, axisScale)
        }

        val sensitivity = effectiveThreshold(globalSensitivity) * axisScale
        val flipYMargin = 1.5f * axisScale
        val swingMargin = 1.0f * axisScale
        val tiltBackMargin = 0.5f * axisScale
        val swingSideMargin = 0.5f * axisScale

        if (absY > sensitivity) {
            return when {
                axisY > sensitivity + flipYMargin -> ShakeGestureType.RIGHT_FLIP
                axisY < -sensitivity - flipYMargin -> ShakeGestureType.LEFT_FLIP
                else -> null
            }
        }

        val swingThreshold = sensitivity - swingMargin
        if (absX > swingThreshold) {
            return when {
                axisX > swingThreshold -> ShakeGestureType.FORWARD_FLIP
                axisX < -sensitivity - tiltBackMargin -> ShakeGestureType.BACKWARD_FLIP
                else -> null
            }
        }

        if (absZ > swingThreshold) {
            return when {
                axisZ > sensitivity + swingSideMargin -> ShakeGestureType.LEFT_FLICK
                axisZ < -sensitivity - swingSideMargin -> ShakeGestureType.RIGHT_FLICK
                else -> null
            }
        }

        return null
    }

    private fun detectPerDirection(
        axisX: Float,
        axisY: Float,
        axisZ: Float,
        axisScale: Float = 1f,
    ): ShakeGestureType? {
        val flipRight = sensitivityFor(ShakeGestureType.RIGHT_FLIP) * axisScale
        val flipLeft = sensitivityFor(ShakeGestureType.LEFT_FLIP) * axisScale
        val tiltFront = sensitivityFor(ShakeGestureType.FORWARD_FLIP) * axisScale
        val tiltBack = sensitivityFor(ShakeGestureType.BACKWARD_FLIP) * axisScale
        val swingLeft = sensitivityFor(ShakeGestureType.LEFT_FLICK) * axisScale
        val swingRight = sensitivityFor(ShakeGestureType.RIGHT_FLICK) * axisScale
        val swingMargin = 1.0f * axisScale

        if (axisY > flipRight || axisY < -flipLeft) {
            return when {
                axisY > flipRight -> ShakeGestureType.RIGHT_FLIP
                axisY < -flipLeft -> ShakeGestureType.LEFT_FLIP
                else -> null
            }
        }

        if (axisX > tiltFront - swingMargin || axisX < -tiltBack + swingMargin) {
            return when {
                axisX > tiltFront -> ShakeGestureType.FORWARD_FLIP
                axisX < -tiltBack -> ShakeGestureType.BACKWARD_FLIP
                else -> null
            }
        }

        if (axisZ > swingLeft - swingMargin || axisZ < -swingRight + swingMargin) {
            return when {
                axisZ > swingLeft -> ShakeGestureType.LEFT_FLICK
                axisZ < -swingRight -> ShakeGestureType.RIGHT_FLICK
                else -> null
            }
        }

        return null
    }

    private fun sensitivityFor(type: ShakeGestureType): Float =
        if (independentEnabled) {
            perDirectionSensitivity[type] ?: globalSensitivity
        } else {
            globalSensitivity
        }.coerceIn(1f, 10f)

    private fun isReverseDirection(current: ShakeGestureType, previous: ShakeGestureType): Boolean =
        when (current) {
            ShakeGestureType.LEFT_FLIP -> previous == ShakeGestureType.RIGHT_FLIP
            ShakeGestureType.RIGHT_FLIP -> previous == ShakeGestureType.LEFT_FLIP
            ShakeGestureType.FORWARD_FLIP -> previous == ShakeGestureType.BACKWARD_FLIP
            ShakeGestureType.BACKWARD_FLIP -> previous == ShakeGestureType.FORWARD_FLIP
            ShakeGestureType.LEFT_FLICK -> previous == ShakeGestureType.RIGHT_FLICK
            ShakeGestureType.RIGHT_FLICK -> previous == ShakeGestureType.LEFT_FLICK
        }

    private fun roundAxis(value: Float): Float =
        (value * 1000f).roundToInt() / 1000f

    companion object {
        private const val TAG = "ShakeGestureDetector"
        private const val NORMAL_COOLDOWN_MS = 500L
        private const val REVERSE_IGNORE_MS = 800L
        /** Scales gyro rad/s thresholds to accelerometer delta m/s². */
        private const val ACCEL_DELTA_SCALE = 0.35f

        /** Reference stores UI slider 1–10 directly as rad/s threshold. */
        fun effectiveThreshold(uiValue: Float): Float = uiValue.coerceIn(1f, 10f)
    }
}
