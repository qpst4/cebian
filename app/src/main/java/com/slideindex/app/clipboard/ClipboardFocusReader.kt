package com.slideindex.app.clipboard

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.os.Build
import android.provider.Settings
import com.slideindex.app.util.PermissionHelper

/**
 * Adapted from [ClipShare](https://github.com/aa2013/ClipShare) (GPL-3.0).
 *
 * ClipShare-style clipboard read: briefly add a 16×16 **focusable** overlay so
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
        if (windowManager == null || !PermissionHelper.canDrawOverlays(appContext)) {
            finishRead(appContext, onResult, ClipboardReader.read(appContext))
            return
        }
        val probe = View(appContext).apply {
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            16,
            16,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.RGBA_8888,
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
        var removed = false
        fun safeRemove() {
            if (!removed) {
                removed = true
                runCatching { windowManager.removeViewImmediate(probe) }
                    .onFailure { runCatching { windowManager.removeView(probe) } }
            }
        }

        probe.post {
            runCatching { probe.requestFocus() }
            val payload = ClipboardReader.read(appContext)
            safeRemove()
            finishRead(appContext, onResult, payload)
        }
        mainHandler.postDelayed({
            safeRemove()
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
