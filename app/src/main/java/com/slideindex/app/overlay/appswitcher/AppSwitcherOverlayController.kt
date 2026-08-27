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
import com.slideindex.app.overlay.layout.FvAppSwitcherSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FvAppSwitcherAxisMergeDirection
import com.slideindex.app.settings.FvAppSwitcherSettings

internal class AppSwitcherOverlayController(
    private val context: Context,
    private val mainHandler: Handler,
) {
    interface Listener {
        fun onLaunch(target: HoneycombRuntimeTarget, longPressArmed: Boolean)
        fun onClosed()
        fun onCircleCountChange(circleCount: Int)
        fun onSettingsChange(settings: FvAppSwitcherSettings)
        fun onLinkAppearanceAxesChange(
            enabled: Boolean,
            mergeDirection: FvAppSwitcherAxisMergeDirection?,
        )
        fun onLinkSlotAxesChange(
            enabled: Boolean,
            mergeDirection: FvAppSwitcherAxisMergeDirection?,
        )
        fun onEditModeChanged(editMode: Boolean)
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: AppSwitcherOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var attached = false
    private var windowTop = 0

    fun isVisible(): Boolean = attached && view != null

    fun isPinned(): Boolean = view?.isPinned() == true

    fun show(
        settings: AppSettings,
        fvSettings: FvAppSwitcherSettings,
        fvLinkAppearanceAxes: Boolean,
        fvLinkSlotAxes: Boolean,
        targets: List<HoneycombRuntimeTarget?>,
        appsByPackage: Map<String, AppInfo>,
        side: FvAppSwitcherSide,
        anchorX: Float,
        anchorY: Float,
        externalTracking: Boolean,
        layoutDensity: Float,
        screenWidth: Float,
        listener: Listener,
    ): Boolean {
        removeNow()
        if (windowManager == null) return false

        lateinit var next: AppSwitcherOverlayView
        next = AppSwitcherOverlayView(
            context = context,
            onLaunch = { target, longPressArmed ->
                removeNow()
                listener.onLaunch(target, longPressArmed)
            },
            onClosed = {
                removeNow()
                listener.onClosed()
            },
            onCircleCountChange = listener::onCircleCountChange,
            onSettingsChange = listener::onSettingsChange,
            onLinkAppearanceAxesChange = listener::onLinkAppearanceAxesChange,
            onLinkSlotAxesChange = listener::onLinkSlotAxesChange,
            onEditModeChanged = listener::onEditModeChanged,
            onPrepareDirectTouch = { activateDirectTouch(next) },
        )
        next.configure(
            settings = settings,
            fvSettings = fvSettings,
            fvLinkAppearanceAxes = fvLinkAppearanceAxes,
            fvLinkSlotAxes = fvLinkSlotAxes,
            targets = targets,
            appsByPackage = appsByPackage,
            side = side,
            anchorX = anchorX,
            anchorY = anchorY,
            externalTracking = externalTracking,
            layoutDensity = layoutDensity,
            screenWidth = screenWidth,
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            displayHeight(),
            OverlayWindowTypes.appSwitcherWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        OverlayWindowTypes.ensureNoBrightnessOverride(params)
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
        } catch (_: Throwable) {
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
            if (cancelled) {
                current.onExternalCancel()
            } else {
                current.onExternalUp(rawX, rawY, cancelled)
            }
            activateDirectTouch(current)
            bringToFrontLocked(current)
        }
    }

    fun bringToFront() {
        val current = view ?: return
        if (!attached) return
        runOnViewThread(current) { bringToFrontLocked(current) }
    }

    fun externalCancel() {
        val current = view ?: return
        runOnViewThread(current, current::onExternalCancel)
    }

    fun enableDirectTouch() {
        val current = view ?: return
        if (!attached) return
        runOnViewThread(current) { activateDirectTouch(current) }
    }

    fun pinForLeaveOpen() {
        val current = view ?: return
        if (!attached) return
        runOnViewThread(current) { current.pinForLeaveOpen() }
    }

    private fun activateDirectTouch(current: AppSwitcherOverlayView) {
        val params = layoutParams ?: return
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        runCatching {
            windowManager?.updateViewLayout(current, params)
            current.enableDirectTouch()
            current.requestFocus()
        }
    }

    private fun bringToFrontLocked(current: AppSwitcherOverlayView) {
        val params = layoutParams ?: return
        val wm = windowManager ?: return
        if (!attached || view !== current) return
        runCatching {
            wm.removeViewImmediate(current)
            wm.addView(current, params)
            current.requestFocus()
        }
    }

    fun refreshTargets(
        fvSettings: FvAppSwitcherSettings,
        targets: List<HoneycombRuntimeTarget?>,
        appsByPackage: Map<String, AppInfo>,
    ) {
        val current = view ?: return
        runOnViewThread(current) { current.refreshTargets(fvSettings, targets, appsByPackage) }
    }

    fun refreshSession(
        fvSettings: FvAppSwitcherSettings,
        fvLinkAppearanceAxes: Boolean,
        fvLinkSlotAxes: Boolean,
        targets: List<HoneycombRuntimeTarget?>,
        appsByPackage: Map<String, AppInfo>,
    ) {
        val current = view ?: return
        runOnViewThread(current) {
            current.refreshSession(
                fvSettings = fvSettings,
                fvLinkAppearanceAxes = fvLinkAppearanceAxes,
                fvLinkSlotAxes = fvLinkSlotAxes,
                targets = targets,
                appsByPackage = appsByPackage,
            )
        }
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
