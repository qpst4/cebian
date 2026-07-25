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
        /** When false, inject asynchronously and restore without waiting for gesture completion. */
        waitForInjection: Boolean = true,
    ) {
        hideTriggers()
        val restored = AtomicBoolean(false)
        val safeRestore = {
            if (restored.compareAndSet(false, true)) {
                showTriggers()
                onComplete()
            }
        }

        // Safety fallback: ensure overlays are restored even if tap injection hangs or fails
        mainHandler.postDelayed(safeRestore, 600L)

        val scheduleInject = {
            runAfterNextFrames(frames = framesBeforeInject) {
                val postRestore = {
                    if (restoreDelayMs <= 0L) {
                        mainHandler.post(safeRestore)
                    } else {
                        mainHandler.postDelayed(safeRestore, restoreDelayMs)
                    }
                }

                if (waitForInjection) {
                    Thread {
                        try {
                            InputTapUtil.dispatchTap(rawX, rawY)
                        } catch (e: Throwable) {
                            Log.e(TAG, "InputTapUtil.dispatchTap failed during passthrough", e)
                        } finally {
                            postRestore()
                        }
                    }.start()
                } else {
                    try {
                        InputTapUtil.dispatchTapAsync(rawX, rawY, onFinished = { _ ->
                            postRestore()
                        })
                    } catch (e: Throwable) {
                        Log.e(TAG, "InputTapUtil.dispatchTapAsync failed during passthrough", e)
                        postRestore()
                    }
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
}
