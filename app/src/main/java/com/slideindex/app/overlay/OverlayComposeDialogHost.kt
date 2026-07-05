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

import com.slideindex.app.service.SlideIndexAccessibilityService

import com.slideindex.app.ui.theme.SlideIndexTheme



class OverlayComposeDialogHost(

    private val context: Context,

    private val themeSeedArgb: () -> Int = { 0xFF6650A4.toInt() },

    private val dynamicColor: () -> Boolean = { false },

) {

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

        val windowManager = windowManager() ?: return

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

                SlideIndexTheme(
                    seedColor = Color(themeSeedArgb()),
                    dynamicColor = dynamicColor(),
                ) {

                    content()

                }

            }

        }

        val params = buildLayoutParams()

        val added = runCatching {

            windowManager.addView(view, params)

            composeView = view

            layoutParams = params

            detachedFromWindow = false

        }.onFailure { error ->

            Log.e(TAG, "Failed to show overlay dialog", error)

            owner?.destroy()

            owner = null

            backPressedHandler = null

        }.isSuccess

        if (!added) return

        view.requestFocus()
        view.post { bringToFront() }

    }



    /** Re-add the dialog so it stays above the presentation overlay after WM layout updates. */

    fun bringToFront() {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { bringToFront() }

            return

        }

        val windowManager = windowManager() ?: return

        val view = composeView ?: return

        val params = layoutParams ?: return

        if (detachedFromWindow) return

        runCatching {

            windowManager.removeView(view)

            windowManager.addView(view, params)

        }.onFailure { error -> Log.e(TAG, "Failed to bring overlay dialog to front", error) }

    }



    fun dismiss() {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { dismiss() }

            return

        }

        val view = composeView ?: return

        windowManager()?.let { wm ->

            runCatching { wm.removeView(view) }

        }

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

        if (detachedFromWindow) return

        windowManager()?.let { wm ->

            runCatching { wm.removeView(view) }

                .onFailure { error -> Log.e(TAG, "Failed to suspend overlay dialog", error) }

        }

        detachedFromWindow = true

    }



    fun restoreToWindow() {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { restoreToWindow() }

            return

        }

        val windowManager = windowManager() ?: return

        val view = composeView ?: return

        val params = layoutParams ?: return

        if (!detachedFromWindow) return

        runCatching {

            windowManager.addView(view, params)

        }.onFailure { error -> Log.e(TAG, "Failed to restore overlay dialog", error) }

        detachedFromWindow = false

    }



    @Deprecated("Use suspendFromWindow / restoreToWindow", ReplaceWith("suspendFromWindow()"))

    fun setVisible(visible: Boolean) {

        if (visible) restoreToWindow() else suspendFromWindow()

    }



    private fun buildLayoutParams(): WindowManager.LayoutParams {

        val host = overlayHostContext() ?: context

        return WindowManager.LayoutParams(

            WindowManager.LayoutParams.MATCH_PARENT,

            WindowManager.LayoutParams.MATCH_PARENT,

            OverlayWindowTypes.overlayWindowType(host),

            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,

            PixelFormat.TRANSLUCENT,

        ).apply {

            gravity = Gravity.TOP or Gravity.START

            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                layoutInDisplayCutoutMode =

                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

            }

        }

    }



    private fun overlayHostContext(): Context? = SlideIndexAccessibilityService.overlayHostContext()



    private fun windowManager(): WindowManager? {

        val host = overlayHostContext() ?: run {

            Log.e(TAG, "Cannot show overlay dialog: accessibility service not connected")

            return null

        }

        return host.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    }



    companion object {

        private const val TAG = "OverlayComposeDialogHost"

    }

}


