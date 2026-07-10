package com.slideindex.app.shake

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object ShakeVibrationHelper {
    fun vibrate(context: Context) {
        val vibrator = resolveVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    45L,
                    VibrationEffect.DEFAULT_AMPLITUDE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(45L)
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            return manager?.defaultVibrator
        }
        @Suppress("DEPRECATION")
        return context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}
