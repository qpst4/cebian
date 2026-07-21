package com.slideindex.app.shake

import kotlin.math.abs
import kotlin.math.sqrt

internal object FaceDownClassifier {
    /** 屏幕朝下平放时，重力沿设备 -Z，az 约为 -9.8 m/s²。 */
    const val FACE_DOWN_Z_ABS_MIN = 8.0f
    const val HORIZONTAL_MAX = 2.5f
    const val GRAVITY_MAG_MIN = 8.5f
    const val GRAVITY_MAG_MAX = 11.5f
    const val ACCEL_DELTA_MAX = 0.35f
    const val GYRO_STILL_MAX = 0.15f

    fun isFaceDownFlat(ax: Float, ay: Float, az: Float): Boolean {
        if (az > -FACE_DOWN_Z_ABS_MIN) return false
        if (abs(ax) > HORIZONTAL_MAX) return false
        if (abs(ay) > HORIZONTAL_MAX) return false
        val magnitude = sqrt(ax * ax + ay * ay + az * az)
        return magnitude in GRAVITY_MAG_MIN..GRAVITY_MAG_MAX
    }

    fun isProximityNear(distanceCm: Float, maxRange: Float): Boolean {
        if (maxRange <= 0f) return false
        val threshold = maxOf(maxRange * 0.2f, 1f)
        return distanceCm < threshold
    }

    fun isAccelerometerStill(
        previousAx: Float,
        previousAy: Float,
        previousAz: Float,
        ax: Float,
        ay: Float,
        az: Float,
    ): Boolean {
        val dx = ax - previousAx
        val dy = ay - previousAy
        val dz = az - previousAz
        return sqrt(dx * dx + dy * dy + dz * dz) <= ACCEL_DELTA_MAX
    }

    fun isGyroStill(gx: Float, gy: Float, gz: Float): Boolean =
        abs(gx) <= GYRO_STILL_MAX &&
            abs(gy) <= GYRO_STILL_MAX &&
            abs(gz) <= GYRO_STILL_MAX
}
