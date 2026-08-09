package com.slideindex.app.util

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.service.InputMethodPickerTrampolineActivity
import com.slideindex.app.service.SlideIndexAccessibilityService

object InputMethodHelper {
    private const val TAG = "InputMethodHelper"
    private const val FOCUS_SETTLE_MS = 250L
    private const val FOCUS_HOST_KEEP_MS = 1_500L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var focusHostView: View? = null

    fun switchInputMethod(context: Context): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { switchInputMethod(context) }
            return true
        }
        // Trampoline (or any Activity) already owns a focused window — call IMM directly.
        if (context is Activity) {
            return showInputMethodPicker(context)
        }
        // Leave-open QL works because a TYPE_ACCESSIBILITY_OVERLAY is focused.
        // Prefer the same path; Activity trampoline alone is still ignored on Flyme
        // even after Displayed.
        if (showViaFocusOverlay()) return true
        return launchPickerTrampoline(context)
    }

    fun showInputMethodPicker(context: Context): Boolean {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
        imm.showInputMethodPicker()
        return true
    }

    /**
     * Temporarily attaches a focusable accessibility overlay, then shows the IME picker
     * from that window's client (same mechanism as leave-open quick launcher).
     */
    private fun showViaFocusOverlay(): Boolean {
        val host = SlideIndexAccessibilityService.overlayHostContext() ?: return false
        val wm = host.getSystemService(WindowManager::class.java) ?: return false
        dismissFocusHost(wm)
        val view = View(host).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            OverlayWindowTypes.overlayWindowType(host),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
        return runCatching {
            wm.addView(view, params)
            focusHostView = view
            view.post {
                view.requestFocus()
                view.postDelayed({
                    if (focusHostView !== view) return@postDelayed
                    val ok = showInputMethodPicker(view.context)
                    Log.i(TAG, "IME picker via focus overlay: ok=$ok hasFocus=${view.hasWindowFocus()}")
                    view.postDelayed({ dismissFocusHost(wm) }, FOCUS_HOST_KEEP_MS)
                }, FOCUS_SETTLE_MS)
            }
            true
        }.getOrElse { error ->
            Log.w(TAG, "focus overlay for IME picker failed", error)
            dismissFocusHost(wm)
            false
        }
    }

    private fun dismissFocusHost(wm: WindowManager) {
        val view = focusHostView ?: return
        focusHostView = null
        runCatching {
            if (view.isAttachedToWindow) {
                wm.removeView(view)
            }
        }.onFailure { Log.w(TAG, "remove IME focus host failed", it) }
    }

    private fun launchPickerTrampoline(context: Context): Boolean {
        val appContext = context.applicationContext
        return runCatching {
            appContext.startActivity(InputMethodPickerTrampolineActivity.createIntent(appContext))
            true
        }.getOrElse { error ->
            Log.w(TAG, "IME picker trampoline failed, falling back to direct call", error)
            showInputMethodPicker(appContext)
        }
    }
}
