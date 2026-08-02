package com.slideindex.app.clipboard

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.slideindex.app.overlay.OverlayWindowTypes

/**
 * FV / ClipShare-style clipboard read: briefly add a 1×1 **focusable** overlay so
 * [ClipboardManager] is readable on Android 10+.
 */
object ClipboardFocusReader {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var inFlight = false
    private val pendingCallbacks = ArrayDeque<(ClipboardPayload?) -> Unit>()

    fun read(context: Context, onResult: (ClipboardPayload?) -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            readOnMain(context.applicationContext, onResult)
        } else {
            mainHandler.post { readOnMain(context.applicationContext, onResult) }
        }
    }

    private fun readOnMain(appContext: Context, onResult: (ClipboardPayload?) -> Unit) {
        if (inFlight) {
            pendingCallbacks.addLast(onResult)
            return
        }
        inFlight = true
        val windowManager = appContext.getSystemService(WindowManager::class.java)
        if (windowManager == null) {
            finishRead(appContext, onResult, ClipboardReader.read(appContext))
            return
        }
        val probe = View(appContext).apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            OverlayWindowTypes.overlayWindowType(appContext),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
        }
        val added = runCatching { windowManager.addView(probe, params) }.isSuccess
        if (!added) {
            finishRead(appContext, onResult, ClipboardReader.read(appContext))
            return
        }
        probe.post {
            runCatching { probe.requestFocus() }
            val payload = ClipboardReader.read(appContext)
            runCatching { windowManager.removeView(probe) }
            finishRead(appContext, onResult, payload)
        }
        mainHandler.postDelayed({
            runCatching { windowManager.removeView(probe) }
        }, 200)
    }

    private fun finishRead(
        appContext: Context,
        onResult: (ClipboardPayload?) -> Unit,
        payload: ClipboardPayload?,
    ) {
        inFlight = false
        onResult(payload)
        val nextCallback = pendingCallbacks.removeFirstOrNull()
        if (nextCallback != null) {
            mainHandler.post { readOnMain(appContext, nextCallback) }
        }
    }
}
