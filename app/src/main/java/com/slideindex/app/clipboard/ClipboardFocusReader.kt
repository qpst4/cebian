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
 * FV-style clipboard read: briefly add a 1×1 focusable overlay so [ClipboardManager] is readable on Android 10+.
 */
object ClipboardFocusReader {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun read(context: Context, onResult: (String?) -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            readOnMain(context.applicationContext, onResult)
        } else {
            mainHandler.post { readOnMain(context.applicationContext, onResult) }
        }
    }

    private fun readOnMain(appContext: Context, onResult: (String?) -> Unit) {
        val windowManager = appContext.getSystemService(WindowManager::class.java)
        if (windowManager == null) {
            onResult(null)
            return
        }
        val probe = View(appContext)
        val params = WindowManager.LayoutParams(
            1,
            1,
            OverlayWindowTypes.overlayWindowType(appContext),
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
        }
        val added = runCatching { windowManager.addView(probe, params) }.isSuccess
        if (!added) {
            onResult(ClipboardTextReader.read(appContext))
            return
        }
        probe.post {
            val text = ClipboardTextReader.read(appContext)
            runCatching { windowManager.removeView(probe) }
            onResult(text)
        }
    }
}
