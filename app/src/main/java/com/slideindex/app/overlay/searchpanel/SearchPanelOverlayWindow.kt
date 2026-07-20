package com.slideindex.app.overlay.searchpanel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.util.PermissionHelper

object SearchPanelOverlayWindow {
    private const val TAG = "SearchPanelOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var composeView: android.widget.FrameLayout? = null
    private var owner: OverlayComposeOwner? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var panelVisibilityState: MutableTransitionState<Boolean>? = null

    val isShowing: Boolean get() = composeView != null

    fun show(context: Context): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                result = show(context)
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }
        if (isShowing) {
            composeView?.visibility = View.VISIBLE
            composeView?.post {
                panelVisibilityState?.targetState = true
            }
            return true
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return false
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: run {
            Log.w(TAG, "show: accessibility service not connected")
            return false
        }
        ensureWindow(hostContext)
        composeView?.visibility = View.VISIBLE
        composeView?.post {
            panelVisibilityState?.targetState = true
        }
        return composeView != null
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        panelVisibilityState?.targetState = false
        mainHandler.postDelayed({
            destroyWindow()
        }, 300) // wait for animation
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }
        composeView?.visibility = View.GONE
    }

    fun restore() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restore() }
            return
        }
        if (composeView != null) {
            composeView?.visibility = View.VISIBLE
        }
    }

    private fun ensureWindow(hostContext: Context) {
        if (composeView != null) return
        appContext = hostContext.applicationContext
        windowManager = hostContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        panelVisibilityState = MutableTransitionState(false)

        owner = OverlayComposeOwner()

        composeView = object : android.widget.FrameLayout(hostContext) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        dismiss()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            val cv = ComposeView(hostContext).apply {
                setContent {
                    com.slideindex.app.ui.theme.SlideIndexTheme(dynamicColor = true) {
                        SearchPanelScreen(
                            visibilityState = panelVisibilityState!!,
                            onDismiss = { dismiss() }
                        )
                    }
                }
            }
            addView(cv, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        try {
            windowManager?.addView(composeView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add window", e)
            destroyWindow()
            return
        }

        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    dismiss()
                }
            }
        }
        appContext?.registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    private fun destroyWindow() {
        if (composeView == null) return
        try {
            owner?.destroy()
            windowManager?.removeView(composeView)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing window", e)
        }
        composeView = null
        owner = null
        windowManager = null
        panelVisibilityState = null
        screenOffReceiver?.let {
            appContext?.unregisterReceiver(it)
            screenOffReceiver = null
        }
        appContext = null
    }
}
