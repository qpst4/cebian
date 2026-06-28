package com.slideindex.app.overlay

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import com.slideindex.app.util.InputTapUtil

/**
 * fv-style passthrough: detach trigger overlays → inject tap → restore.
 */
object OverlayPassthrough {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun run(
        hideTriggers: () -> Unit,
        showTriggers: () -> Unit,
        rawX: Float,
        rawY: Float,
        onComplete: () -> Unit,
    ) {
        hideTriggers()
        mainHandler.post {
            runAfterNextFrames(frames = 2) {
                Thread {
                    InputTapUtil.dispatchTap(rawX, rawY)
                    mainHandler.postDelayed({
                        showTriggers()
                        onComplete()
                    }, RESTORE_DELAY_MS)
                }.start()
            }
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

    private const val RESTORE_DELAY_MS = 150L
}
