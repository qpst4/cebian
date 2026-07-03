package com.slideindex.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import com.slideindex.app.ui.theme.SlideIndexTheme

class OverlayComposeDialogHost(
    private val context: Context,
    private val themeSeedArgb: () -> Int = { 0xFF6650A4.toInt() },
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val themedContext = OverlayCompose.themedContext(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var owner: OverlayComposeOwner? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var detachedFromWindow = false
    private var backPressedHandler: (() -> Boolean)? = null

    val isShowing: Boolean get() = composeView != null

    fun show(
        onBackPressed: (() -> Boolean)? = null,
        content: @Composable () -> Unit,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(onBackPressed, content) }
            return
        }
        dismiss()
        backPressedHandler = onBackPressed
        val dialogOwner = OverlayComposeOwner()
        owner = dialogOwner
        val view = OverlayCompose.createComposeView(themedContext, dialogOwner).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) {
                    return@setOnKeyListener false
                }
                if (backPressedHandler?.invoke() == true) {
                    true
                } else {
                    dismiss()
                    true
                }
            }
            setContent {
                SlideIndexTheme(seedColor = Color(themeSeedArgb())) {
                    content()
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        runCatching {
            windowManager.addView(view, params)
            composeView = view
            layoutParams = params
            detachedFromWindow = false
            view.requestFocus()
        }.onFailure { error ->
            Log.e(TAG, "Failed to show overlay dialog", error)
            owner?.destroy()
            owner = null
            backPressedHandler = null
        }
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        val view = composeView ?: return
        runCatching { windowManager.removeView(view) }
        owner?.destroy()
        composeView = null
        layoutParams = null
        detachedFromWindow = false
        owner = null
        backPressedHandler = null
    }

    /** Detach overlay from [WindowManager] while keeping Compose state (visibility alone is unreliable). */
    fun suspendFromWindow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { suspendFromWindow() }
            return
        }
        val view = composeView ?: return
        val params = layoutParams ?: return
        if (detachedFromWindow) return
        runCatching { windowManager.removeView(view) }
            .onFailure { error -> Log.e(TAG, "Failed to suspend overlay dialog", error) }
        detachedFromWindow = true
    }

    fun restoreToWindow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreToWindow() }
            return
        }
        val view = composeView ?: return
        val params = layoutParams ?: return
        if (!detachedFromWindow) return
        runCatching {
            windowManager.addView(view, params)
            view.requestFocus()
        }.onFailure { error -> Log.e(TAG, "Failed to restore overlay dialog", error) }
        detachedFromWindow = false
    }

    @Deprecated("Use suspendFromWindow / restoreToWindow", ReplaceWith("suspendFromWindow()"))
    fun setVisible(visible: Boolean) {
        if (visible) restoreToWindow() else suspendFromWindow()
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

    companion object {
        private const val TAG = "OverlayComposeDialogHost"
    }
}
