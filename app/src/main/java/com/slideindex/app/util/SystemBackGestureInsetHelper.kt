package com.slideindex.app.util

import android.content.Context
import android.provider.Settings

object SystemBackGestureInsetHelper {
    const val KEY_LEFT = "back_gesture_inset_scale_left"
    const val KEY_RIGHT = "back_gesture_inset_scale_right"
    const val DEFAULT_SCALE = 1f
    const val MIN_SCALE = 0f
    const val MAX_SCALE = 1f

    fun readLeftScale(context: Context): Float = readScale(context, KEY_LEFT)

    fun readRightScale(context: Context): Float = readScale(context, KEY_RIGHT)

    private fun readScale(context: Context, key: String): Float =
        Settings.Secure.getFloat(context.contentResolver, key, DEFAULT_SCALE)
            .coerceIn(MIN_SCALE, MAX_SCALE)

    fun writeLeftScale(context: Context, value: Float): Boolean =
        writeScale(context, KEY_LEFT, value)

    fun writeRightScale(context: Context, value: Float): Boolean =
        writeScale(context, KEY_RIGHT, value)

    fun resetDefaults(context: Context): Boolean {
        val leftOk = writeScale(context, KEY_LEFT, DEFAULT_SCALE)
        val rightOk = writeScale(context, KEY_RIGHT, DEFAULT_SCALE)
        return leftOk && rightOk
    }

    fun canWrite(context: Context): Boolean =
        SecureSettingsHelper.hasWriteSecureSettings(context) ||
            TaskManagerUtil.hasPrivilegedAccess()

    private fun writeScale(context: Context, key: String, value: Float): Boolean {
        val clamped = value.coerceIn(MIN_SCALE, MAX_SCALE)
        if (SecureSettingsHelper.hasWriteSecureSettings(context)) {
            return Settings.Secure.putFloat(context.contentResolver, key, clamped)
        }
        if (TaskManagerUtil.hasPrivilegedAccess()) {
            return TaskManagerUtil.runShellCommandLine(
                command = "settings put secure $key $clamped",
                useRoot = false,
            ).success
        }
        return false
    }
}
