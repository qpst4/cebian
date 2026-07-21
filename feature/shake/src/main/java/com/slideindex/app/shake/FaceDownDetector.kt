package com.slideindex.app.shake

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log

class FaceDownDetector(
    context: Context,
    private val onTriggered: () -> Unit,
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val sensorHandler = Handler(Looper.getMainLooper())

    val isAvailable: Boolean get() = accelerometer != null

    private var listenerRegistered = false
    private var holdDurationMs = FaceDownGestureSettings.clampHoldDurationMs(800L)
    private var requireProximity = false

    private var holdStartMs = 0L
    private var lastAccel = floatArrayOf(0f, 0f, 0f)
    private var accelReady = false
    private var proximityNear = false
    private var proximityMaxRange = 0f
    private var hasProximitySensor = false
    private var gyroStill = true
    private var hasGyroscope = false

    fun configure(holdDurationMs: Long, requireProximity: Boolean) {
        this.holdDurationMs = FaceDownGestureSettings.clampHoldDurationMs(holdDurationMs)
        this.requireProximity = requireProximity
    }

    fun start() {
        stop()
        val sensor = accelerometer
        if (sensor == null) {
            Log.w(TAG, "start: no accelerometer")
            return
        }
        hasProximitySensor = proximity != null
        hasGyroscope = gyroscope != null
        proximityMaxRange = proximity?.maximumRange ?: 0f
        proximityNear = !hasProximitySensor
        gyroStill = !hasGyroscope
        accelReady = false
        holdStartMs = 0L

        var registered = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            sensorHandler,
        )
        proximity?.let {
            registered = sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL,
                sensorHandler,
            ) && registered
        }
        gyroscope?.let {
            registered = sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL,
                sensorHandler,
            ) && registered
        }
        listenerRegistered = registered
        Log.d(
            TAG,
            "start: registered=$registered proximity=$hasProximitySensor gyro=$hasGyroscope " +
                "hold=${holdDurationMs}ms requireProximity=$requireProximity",
        )
    }

    fun stop() {
        if (listenerRegistered) {
            sensorManager.unregisterListener(this)
            listenerRegistered = false
        }
        holdStartMs = 0L
        accelReady = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
            Sensor.TYPE_PROXIMITY -> handleProximity(event)
            Sensor.TYPE_GYROSCOPE -> handleGyroscope(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

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

        val now = System.currentTimeMillis()
        if (!FaceDownClassifier.isFaceDownFlat(ax, ay, az)) {
            resetHold()
            lastAccel[0] = ax
            lastAccel[1] = ay
            lastAccel[2] = az
            return
        }
        if (requireProximity && hasProximitySensor && !proximityNear) {
            resetHold()
            lastAccel[0] = ax
            lastAccel[1] = ay
            lastAccel[2] = az
            return
        }
        if (!FaceDownClassifier.isAccelerometerStill(lastAccel[0], lastAccel[1], lastAccel[2], ax, ay, az)) {
            resetHold()
            lastAccel[0] = ax
            lastAccel[1] = ay
            lastAccel[2] = az
            return
        }
        if (hasGyroscope && !gyroStill) {
            resetHold()
            lastAccel[0] = ax
            lastAccel[1] = ay
            lastAccel[2] = az
            return
        }

        lastAccel[0] = ax
        lastAccel[1] = ay
        lastAccel[2] = az

        val elapsedMs = if (holdStartMs == 0L) {
            holdStartMs = now
            0L
        } else {
            now - holdStartMs
        }
        if (elapsedMs >= holdDurationMs) {
            Log.i(TAG, "face-down hold satisfied after ${elapsedMs}ms")
            resetHold()
            onTriggered()
        }
    }

    private fun handleProximity(event: SensorEvent) {
        proximityNear = FaceDownClassifier.isProximityNear(event.values[0], proximityMaxRange)
    }

    private fun handleGyroscope(event: SensorEvent) {
        gyroStill = FaceDownClassifier.isGyroStill(event.values[0], event.values[1], event.values[2])
    }

    private fun resetHold() {
        holdStartMs = 0L
    }

    companion object {
        private const val TAG = "FaceDownDetector"
    }
}
