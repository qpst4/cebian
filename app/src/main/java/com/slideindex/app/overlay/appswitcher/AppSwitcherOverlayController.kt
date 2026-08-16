package com.slideindex.app.overlay.appswitcher

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.slideindex.app.data.AppInfo
import com.slideindex.app.overlay.HoneycombRuntimeTarget
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.settings.AppSettings

internal class AppSwitcherOverlayController(
    private val context: Context,
    private val mainHandler: Handler,
) {
    interface Listener {
        fun onLaunch(target: HoneycombRuntimeTarget, selectionPressDurationMs: Long)
        fun onClosed()
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: AppSwitcherOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var attached = false
    private var windowTop = 0

    fun isVisible(): Boolean = attached && view != null

    fun show(
        settings: AppSettings,
        targets: List<HoneycombRuntimeTarget>,
        appsByPackage: Map<String, AppInfo>,
        side: com.slideindex.app.overlay.layout.AppSwitcherSide,
        anchorRawY: Float,
        externalTracking: Boolean,
        listener: Listener,
    ): Boolean {
        removeNow()
        if (windowManager == null || targets.isEmpty()) return false

        val display = settings.appSwitcherDisplay
        val usesNativeWindowBlur = display.blurDp > 0 && runCatching {
            windowManager.isCrossWindowBlurEnabled
        }.getOrDefault(false)

        val density = context.resources.displayMetrics.density
        val next = AppSwitcherOverlayView(
            context = context,
            onLaunch = { target, duration ->
                removeNow()
                listener.onLaunch(target, duration)
            },
            onClosed = {
                removeNow()
                listener.onClosed()
            },
        )
        next.configure(
            settings = settings,
            targets = targets,
            appsByPackage = appsByPackage,
            side = side,
            anchorRawY = anchorRawY,
            externalTracking = externalTracking,
            density = density,
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            displayHeight(),
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        OverlayWindowTypes.ensureNoBrightnessOverride(params)
        if (usesNativeWindowBlur) {
            val rawBlurPx = (display.blurDp * density).toInt().coerceIn(1, 80)
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            params.setBlurBehindRadius(rawBlurPx)
        }
        if (externalTracking) {
            params.flags = params.flags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        params.gravity = Gravity.TOP or Gravity.START
        params.y = windowTop
        params.setFitInsetsTypes(0)
        params.title = "SlideIndexAppSwitcher"
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS

        return try {
            windowManager.addView(next, params)
            view = next
            layoutParams = params
            attached = true
            next.post {
                runCatching {
                    next.windowInsetsController?.let { controller ->
                        controller.systemBarsBehavior =
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(WindowInsets.Type.navigationBars())
                    }
                }
            }
            next.beginSession()
            true
        } catch (error: Throwable) {
            view = null
            attached = false
            false
        }
    }

    fun externalMove(rawX: Float, rawY: Float) {
        val current = view ?: return
        if (!attached) return
        current.postOnAnimation { current.onExternalMove(rawX, rawY) }
    }

    fun externalUp(rawX: Float, rawY: Float, cancelled: Boolean) {
        val current = view ?: return
        if (!attached) return
        runOnViewThread(current) {
            if (!cancelled) current.onExternalMove(rawX, rawY)
            current.onExternalUp(rawX, rawY, cancelled)
        }
    }

    fun externalCancel() {
        val current = view ?: return
        runOnViewThread(current, current::onExternalCancel)
    }

    fun enableDirectTouch() {
        val current = view ?: return
        val params = layoutParams ?: return
        if (!attached) return
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        runOnViewThread(current) {
            runCatching {
                windowManager?.updateViewLayout(current, params)
                current.enableDirectTouch()
                current.requestFocus()
            }
        }
    }

    fun refreshTargets(targets: List<HoneycombRuntimeTarget>, appsByPackage: Map<String, AppInfo>) {
        val current = view ?: return
        runOnViewThread(current) { current.refreshTargets(targets, appsByPackage) }
    }

    fun dismiss() {
        val current = view ?: return
        runOnViewThread(current, current::dismissNow)
    }

    fun removeNow() {
        val current = view
        view = null
        if (!attached || current == null || windowManager == null) {
            attached = false
            return
        }
        attached = false
        layoutParams = null
        windowTop = 0
        val removal: () -> Unit = {
            runCatching { windowManager.removeViewImmediate(current) }
        }
        val owner = current.handler
        if (owner != null && owner.looper != Looper.myLooper()) {
            owner.post(removal)
        } else {
            removal()
        }
    }

    private fun displayHeight(): Int =
        windowManager?.currentWindowMetrics?.bounds?.height()
            ?: context.resources.displayMetrics.heightPixels

    private fun runOnViewThread(current: AppSwitcherOverlayView, action: () -> Unit) {
        if (!attached || view !== current) return
        val owner = current.handler
        if (owner != null && owner.looper != Looper.myLooper()) {
            owner.post {
                if (attached && view === current) action()
            }
        } else if (attached && view === current) {
            action()
        }
    }
}
