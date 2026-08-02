package com.slideindex.app.clipboard.monitor

/**
 * Based on [ClipboardListener](https://github.com/aa2013/ClipboardListener) (MIT).
 */
import android.os.Handler
import android.os.Looper

/** Gates clipboard FGS startup until the main process Application has finished onCreate. */
internal object ClipboardMonitorStartup {
    @Volatile
    var applicationReady: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())

    fun runOnMainWhenIdle(block: () -> Unit) {
        runOnMainWhenReady {
            postWhenIdle(block)
        }
    }

    private fun postWhenIdle(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Looper.myQueue().addIdleHandler {
                block()
                false
            }
        } else {
            mainHandler.post { postWhenIdle(block) }
        }
    }

    fun runOnMainWhenReady(block: () -> Unit) {
        if (applicationReady) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                block()
            } else {
                mainHandler.post(block)
            }
            return
        }
        mainHandler.post {
            if (applicationReady) {
                block()
            } else {
                mainHandler.postDelayed({ runOnMainWhenReady(block) }, RETRY_MS)
            }
        }
    }

    private const val RETRY_MS = 200L
}
