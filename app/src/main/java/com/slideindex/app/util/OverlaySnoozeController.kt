package com.slideindex.app.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.slideindex.app.R

/** 临时暂停触钮、边角轮盘与悬浮球的运行时状态（不落盘）。 */
object OverlaySnoozeController {
    const val DEFAULT_DURATION_MS = 5_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeUntilUptimeMillis = 0L
    private var expiryRunnable: Runnable? = null

    var onStateChanged: (() -> Unit)? = null

    fun isActive(): Boolean = SystemClock.uptimeMillis() < activeUntilUptimeMillis

    fun snooze(context: Context, durationMs: Long = DEFAULT_DURATION_MS) {
        val safeDuration = durationMs.coerceAtLeast(1L)
        activeUntilUptimeMillis = SystemClock.uptimeMillis() + safeDuration
        scheduleExpiry()
        onStateChanged?.invoke()
        Toast.makeText(
            context.applicationContext,
            context.getString(R.string.gesture_action_snooze_overlays_toast),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun cancel() {
        expiryRunnable?.let(mainHandler::removeCallbacks)
        expiryRunnable = null
        if (activeUntilUptimeMillis == 0L) return
        activeUntilUptimeMillis = 0L
        onStateChanged?.invoke()
    }

    private fun scheduleExpiry() {
        expiryRunnable?.let(mainHandler::removeCallbacks)
        val delayMs = (activeUntilUptimeMillis - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        val runnable = Runnable {
            expiryRunnable = null
            if (!isActive()) {
                onStateChanged?.invoke()
            }
        }
        expiryRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }
}
