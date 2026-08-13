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
import android.widget.FrameLayout
import androidx.compose.animation.core.MutableTransitionState
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.slideindex.app.overlay.FloatBallOverlay
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayPanelSystemGestureExclusion
import com.slideindex.app.overlay.OverlayTextToolbarProvider
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.compositor.OverlaySceneController
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.settings.SearchPanelBackgroundStyle
import com.slideindex.app.util.PermissionHelper
import kotlin.math.roundToInt

object SearchPanelOverlayWindow {
    private const val TAG = "SearchPanelOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var composeViewRef = java.lang.ref.WeakReference<FrameLayout>(null)
    private var composeView: FrameLayout?
        get() = composeViewRef.get()
        set(value) {
            composeViewRef = java.lang.ref.WeakReference(value)
        }
    private var owner: OverlayComposeOwner? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var appContext: android.app.Application? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var panelVisibilityState: MutableTransitionState<Boolean>? = null
    private var bringAboveToken = 0
    private var dismissToken = 0

    /** Warm-up keeps [composeView] alive while hidden; only treat visible window as showing. */
    val isShowing: Boolean get() = composeView?.visibility == View.VISIBLE

    /** True when [FLAG_BLUR_BEHIND] is active — Compose must not draw screenshot blur. */
    val isNativeBlurActive: Boolean get() = nativeBlurActive
    private var nativeBlurActive = false

    /** Float ball may finish attaching after the panel window; retry z-order fixes. */
    private fun scheduleBringFloatBallAbovePanels() {
        val token = ++bringAboveToken
        fun attempt() {
            if (token != bringAboveToken) return
            FloatBallOverlay.scheduleChromeAbovePanels()
        }
        attempt()
        composeView?.post {
            attempt()
            composeView?.postOnAnimation { attempt() }
        }
        mainHandler.postDelayed({ attempt() }, 200)
        mainHandler.postDelayed({ attempt() }, 800)
    }

    fun warmUp(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { warmUp(context) }
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        ensureWindow(hostContext)
        if (!isShowing) {
            applyPanelShellPassive()
        }
    }

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
        ++dismissToken
        if (isShowing) {
            applyPanelShellActive()
            composeView?.post {
                panelVisibilityState?.targetState = true
            }
            OverlaySceneController.onContentPanelShown()
            scheduleBringFloatBallAbovePanels()
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
        applyPanelShellActive()
        composeView?.post {
            panelVisibilityState?.targetState = true
        }
        OverlaySceneController.onContentPanelShown()
        scheduleBringFloatBallAbovePanels()
        return composeView != null
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        ++bringAboveToken
        val token = ++dismissToken
        SearchPanelSessionState.persistBeforeDismiss?.invoke()
        panelVisibilityState?.targetState = false
        mainHandler.postDelayed({
            if (token != dismissToken) return@postDelayed
            if (panelVisibilityState?.targetState == true) return@postDelayed
            applyPanelShellPassive()
            OverlaySceneController.onContentPanelHidden()
        }, SEARCH_PANEL_ANIM_MS.toLong())
    }

    /** @return true if back was handled (preview dismissed or panel closed). */
    fun handleBack(): Boolean {
        if (!isShowing) return false
        if (SearchPanelSessionState.onBackPressed?.invoke() == true) {
            return true
        }
        dismiss()
        return true
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }
        applyPanelShellPassive()
    }

    fun restore() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restore() }
            return
        }
        if (composeView != null) {
            ++dismissToken
            applyPanelShellActive()
            scheduleBringFloatBallAbovePanels()
        }
    }

    /** Applies cross-window blur when BLUR mode is active (matches honeycomb overlay). */
    fun updateBackgroundBlur(context: Context, backgroundStyle: Int, blurRadiusDp: Int): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                result = updateBackgroundBlur(context, backgroundStyle, blurRadiusDp)
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }
        val wm = windowManager ?: return false
        val view = composeView ?: return false
        val params = layoutParams ?: return false

        val wantsNativeBlur = backgroundStyle == SearchPanelBackgroundStyle.BLUR &&
            blurRadiusDp > 0
        val canNativeBlur = wantsNativeBlur && runCatching { wm.isCrossWindowBlurEnabled }
            .getOrDefault(false)
        nativeBlurActive = canNativeBlur

        if (canNativeBlur) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            val density = context.resources.displayMetrics.density
            val rawBlurPx = (blurRadiusDp * density).roundToInt()
            val clampedBlurPx = rawBlurPx.coerceIn(1, 80)
            params.setBlurBehindRadius(clampedBlurPx)
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            params.setBlurBehindRadius(0)
        }
        runCatching { wm.updateViewLayout(view, params) }
        return nativeBlurActive
    }

    /** Invisible prefetch shell: must not intercept touches beneath the system UI. */
    private fun applyPanelShellPassive() {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        view.clearFocus()
        view.visibility = View.GONE
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun applyPanelShellActive() {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        view.visibility = View.VISIBLE
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        runCatching { wm.updateViewLayout(view, params) }
        view.requestFocus()
    }

    private fun ensureWindow(hostContext: Context) {
        if (composeView != null) return
        appContext = hostContext.applicationContext as android.app.Application
        windowManager = hostContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        panelVisibilityState = MutableTransitionState(false)

        owner = OverlayComposeOwner()

        composeView = object : FrameLayout(hostContext) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        handleBack()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            visibility = View.GONE
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            val cv = OverlayCompose.createComposeView(hostContext, owner!!).apply {
                setContent {
                    com.slideindex.app.ui.theme.OverlayAwareModuleTheme {
                        OverlayTextToolbarProvider {
                            SearchPanelScreen(
                                visibilityState = panelVisibilityState!!,
                                onDismiss = { dismiss() },
                            )
                        }
                    }
                }
            }
            addView(cv, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        // Start passive: warmUp attaches this window early and must not eat screen touches.
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(hostContext),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        try {
            windowManager?.addView(composeView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add window", e)
            destroyWindow()
            return
        }
        if (FloatBallOverlay.isShowing) {
            FloatBallOverlay.scheduleChromeAbovePanels(delayMs = 0L)
        }
        composeView?.let { OverlayPanelSystemGestureExclusion.attach(it) }

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
        layoutParams = null
        nativeBlurActive = false
        panelVisibilityState = null
        screenOffReceiver?.let {
            appContext?.unregisterReceiver(it)
            screenOffReceiver = null
        }
        appContext = null
    }
}
