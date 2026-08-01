package com.slideindex.app.overlay

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import com.slideindex.app.util.InputTapUtil
import java.util.concurrent.atomic.AtomicBoolean

/**
 * fv-style passthrough: detach trigger overlays → inject tap → restore.
 */
object OverlayPassthrough {
    private const val TAG = "OverlayPassthrough"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun run(
        hideTriggers: () -> Unit,
        showTriggers: () -> Unit,
        rawX: Float,
        rawY: Float,
        onComplete: () -> Unit,
        framesBeforeInject: Int = DEFAULT_FRAMES_BEFORE_INJECT,
        restoreDelayMs: Long = DEFAULT_RESTORE_DELAY_MS,
    ) {
        hideTriggers()
        val restored = AtomicBoolean(false)
        var safetyRestore: Runnable? = null

        val safeRestore = {
            if (restored.compareAndSet(false, true)) {
                safetyRestore?.let { mainHandler.removeCallbacks(it) }
                showTriggers()
                onComplete()
            }
        }

        safetyRestore = Runnable {
            Log.w(TAG, "Passthrough safety restore at ($rawX, $rawY)")
            safeRestore()
        }
        mainHandler.postDelayed(safetyRestore!!, SAFETY_RESTORE_MS)

        val scheduleInject = {
            runAfterNextFrames(frames = framesBeforeInject) {
                try {
                    InputTapUtil.dispatchTapAsync(rawX, rawY, onFinished = { ok ->
                        if (!ok) {
                            Log.w(TAG, "dispatchTapAsync failed at ($rawX, $rawY)")
                        }
                        if (restoreDelayMs <= 0L) {
                            mainHandler.post(safeRestore)
                        } else {
                            mainHandler.postDelayed(safeRestore, restoreDelayMs)
                        }
                    })
                } catch (e: Throwable) {
                    Log.e(TAG, "InputTapUtil.dispatchTapAsync failed during passthrough", e)
                    safeRestore()
                }
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            scheduleInject()
        } else {
            mainHandler.post(scheduleInject)
        }
    }

    private fun runAfterNextFrames(frames: Int, action: () -> Unit) {
        if (frames <= 0) {
            action()
            return
        }
        Choreographer.getInstance().postFrameCallback {
            runAfterNextFrames(frames - 1, action)
        }
    }

    private const val DEFAULT_FRAMES_BEFORE_INJECT = 2
    private const val DEFAULT_RESTORE_DELAY_MS = 150L
    private const val SAFETY_RESTORE_MS = 2000L
}
