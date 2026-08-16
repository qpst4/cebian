package com.slideindex.app.overlay.appswitcher

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.settings.AppSwitcherDisplaySettings
import com.slideindex.app.util.PermissionHelper

@SuppressLint("StaticFieldLeak")
object AppSwitcherLayoutPreviewHost {
    private const val TAG = "AppSwitcherPreview"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var previewRoot: FrameLayout? = null
    private var previewView: AppSwitcherLayoutPreviewView? = null
    private var previewParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var previewAttached = false
    private var previewActive = false
    private var display = AppSwitcherDisplaySettings()
    private var density = 1f

    fun setActive(context: Context, active: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setActive(context, active) }
            return
        }
        previewActive = active
        if (active) {
            ensurePreview(context)
            syncWindow()
        } else {
            detach()
        }
    }

    fun updateDisplay(context: Context, next: AppSwitcherDisplaySettings) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateDisplay(context, next) }
            return
        }
        display = next
        if (!previewActive) return
        ensurePreview(context)
        previewView?.update(display, density)
    }

    private fun ensurePreview(context: Context) {
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: return
        if (previewView == null) {
            density = hostContext.resources.displayMetrics.density
            previewView = AppSwitcherLayoutPreviewView(hostContext)
            previewRoot = FrameLayout(hostContext).apply {
                addView(
                    previewView,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            }
            previewParams = OverlayWindowTypes.createPresentationParams(hostContext).apply {
                OverlayWindowTypes.applyFullScreen(this)
            }
            windowManager = hostContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }
        previewView?.update(display, density)
    }

    private fun syncWindow() {
        if (!previewActive) {
            detach()
            return
        }
        val root = previewRoot ?: return
        val params = previewParams ?: return
        val wm = windowManager ?: return
        OverlayWindowTypes.applyFullScreen(params)
        OverlayWindowTypes.applyPresentationPassthroughFlags(params)
        if (!previewAttached) {
            OverlayWindowTypes.ensureNoBrightnessOverride(params)
            runCatching { wm.addView(root, params) }
                .onSuccess { previewAttached = true }
                .onFailure { Log.e(TAG, "Failed to attach app switcher layout preview", it) }
        } else {
            previewView?.update(display, density)
        }
    }

    private fun detach() {
        previewActive = false
        if (!previewAttached) return
        previewRoot?.let { root ->
            runCatching { windowManager?.removeView(root) }
        }
        previewAttached = false
    }
}
