package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages a single full-screen overlay [ComposeView] window: attach, focus, screen-off, destroy.
 * Used by side panels, translate panel, and pick-result panel.
 */
class OverlayFullScreenPanelHost(
    private val tag: String,
    private val layoutParamsFactory: (Context, Boolean) -> WindowManager.LayoutParams =
        { context, focusable -> OverlayPanelLayoutParams.fullScreenOverlay(context, focusable) },
    private val onScreenOff: () -> Unit = {},
    private val excludeLeftBackEdge: Boolean = true,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null

    val isAttached: Boolean get() = composeView != null

    val composeView: ComposeView? get() = composeViewRef

    val owner: OverlayComposeOwner? get() = ownerRef

    private var composeViewRef: ComposeView? = null
    private var ownerRef: OverlayComposeOwner? = null

    fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    fun ensureWindow(
        context: Context,
        focusable: Boolean = false,
        content: @Composable () -> Unit,
    ): OverlayComposeOwner? {
        if (composeViewRef != null) return ownerRef

        val dialogOwner = OverlayComposeOwner()
        val overlayContext = OverlayCompose.themedContext(context)
        val compose = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setContent { content() }
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            dialogOwner.destroy()
            Log.w(tag, "ensureWindow: WindowManager unavailable")
            return null
        }

        val params = layoutParamsFactory(context, focusable)
        val added = runCatching { wm.addView(compose, params) }
            .onFailure { Log.e(tag, "addView failed", it) }
            .isSuccess
        if (!added) {
            dialogOwner.destroy()
            return null
        }
        if (FloatBallOverlay.isShowing) {
            FloatBallOverlay.notifyPanelAttachedAboveChrome()
        }
        OverlayPanelSystemGestureExclusion.attach(compose, excludeLeftBackEdge = excludeLeftBackEdge)

        windowManager = wm
        composeViewRef = compose
        ownerRef = dialogOwner
        layoutParams = params
        appContext = context
        registerScreenOffReceiver(context)
        return dialogOwner
    }

    fun setViewVisible(visible: Boolean) {
        composeViewRef?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setDragHidden(hidden: Boolean) {
        runOnMain {
            val wm = windowManager ?: return@runOnMain
            val view = composeViewRef ?: return@runOnMain
            val params = layoutParams ?: return@runOnMain
            if (hidden) {
                view.visibility = View.INVISIBLE
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                view.visibility = View.VISIBLE
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            runCatching { wm.updateViewLayout(view, params) }
        }
    }

    /**
     * Cross-window blur for translucent overlay panels (API 31+, [WindowManager.isCrossWindowBlurEnabled]).
     * Returns true when [FLAG_BLUR_BEHIND] is active — Compose should use semi-transparent surfaces.
     */
    fun updateBackgroundBlur(context: Context, blurRadiusDp: Int): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                result = updateBackgroundBlur(context, blurRadiusDp)
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }
        val wm = windowManager ?: return false
        val view = composeViewRef ?: return false
        val params = layoutParams ?: return false

        val wantsNativeBlur = blurRadiusDp > 0
        val canNativeBlur = wantsNativeBlur && runCatching { wm.isCrossWindowBlurEnabled }
            .getOrDefault(false)

        if (canNativeBlur) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            val density = context.resources.displayMetrics.density
            val rawBlurPx = (blurRadiusDp * density).roundToInt()
            params.setBlurBehindRadius(rawBlurPx.coerceIn(1, 80))
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            params.setBlurBehindRadius(0)
        }
        runCatching { wm.updateViewLayout(view, params) }
        return canNativeBlur
    }

    fun setInputActive(active: Boolean, requestRootFocus: Boolean = true) {
        runOnMain {
            val wm = windowManager ?: return@runOnMain
            val view = composeViewRef ?: return@runOnMain
            val params = layoutParams ?: return@runOnMain
            params.flags = if (active) {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            runCatching { wm.updateViewLayout(view, params) }
            if (active) {
                view.isFocusable = true
                view.isFocusableInTouchMode = true
                if (requestRootFocus) {
                    view.requestFocus()
                }
            } else {
                view.clearFocus()
            }
        }
    }

    fun dismissAnimated(
        hideDelayMs: Long = 300L,
        onHidden: () -> Unit = {},
    ) {
        runOnMain {
            setInputActive(false)
            val view = composeViewRef
            val currentOwner = ownerRef
            if (view == null || currentOwner == null) {
                onHidden()
                return@runOnMain
            }
            currentOwner.lifecycleScope.launch(Dispatchers.Main) {
                delay(hideDelayMs)
                view.visibility = View.GONE
                onHidden()
            }
        }
    }

    fun destroy() {
        runOnMain {
            setInputActive(false)
            val currentOwner = ownerRef
            val view = composeViewRef
            val wm = windowManager
            if (currentOwner == null || view == null || wm == null) return@runOnMain

            runCatching { wm.removeView(view) }
            unregisterScreenOffReceiver()
            view.post { currentOwner.destroy() }

            composeViewRef = null
            ownerRef = null
            layoutParams = null
            windowManager = null
            appContext = null
        }
    }

    private fun registerScreenOffReceiver(context: Context) {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    onScreenOff()
                }
            }
        }
        screenOffReceiver = receiver
        runCatching {
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
    }

    private fun unregisterScreenOffReceiver() {
        val receiver = screenOffReceiver
        if (receiver == null) return
        appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
        screenOffReceiver = null
    }
}
