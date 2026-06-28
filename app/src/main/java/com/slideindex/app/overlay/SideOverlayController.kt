package com.slideindex.app.overlay

import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.slideindex.app.data.AppRepository
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.settings.edgeTriggerWidthDp
import com.slideindex.app.settings.interceptWindowWidthDp
import com.slideindex.app.settings.triggerHeightFraction
import com.slideindex.app.settings.triggerTopFraction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SideOverlayController(
    private val context: android.content.Context,
    val side: PanelSide,
    private val windowManager: WindowManager,
    private val appRepository: AppRepository,
    private val scope: CoroutineScope,
) {
    private var settings: AppSettings = AppSettings()
    private var screenHeightPx: Int = 0
    private var screenWidthPx: Int = 0
    private var previewMode = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY

    private val overlayContext = OverlayCompose.themedContext(context)
    private var overlayView: EdgeGestureOverlayView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var loadJob: Job? = null

    private val density get() = context.resources.displayMetrics.density

    fun updateSettings(newSettings: AppSettings, screenWidth: Int) {
        val hiddenChanged = newSettings.hiddenAppPackages != settings.hiddenAppPackages
        settings = newSettings
        screenHeightPx = context.resources.displayMetrics.heightPixels
        screenWidthPx = screenWidth
        overlayView?.applySettings(newSettings, screenWidth)
        if (overlayView != null) {
            preloadApps(force = hiddenChanged)
        }
        if (overlayView != null && windowParams != null &&
            overlayView?.isSessionActive() != true &&
            !previewMode
        ) {
            applyTriggerLayout(windowParams!!)
            applyNormalTouchFlags(windowParams!!)
            runCatching { windowManager.updateViewLayout(overlayView, windowParams) }
        } else if (previewMode) {
            overlayView?.invalidate()
        }
    }

    fun setPreviewMode(enabled: Boolean, content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY) {
        val changed = previewMode != enabled || previewContent != content
        if (!changed) return
        previewMode = enabled
        previewContent = content
        val view = overlayView ?: return
        view.setPreviewMode(enabled, content)
        if (enabled) {
            expandPreviewWindow()
        } else if (!view.isSessionActive()) {
            collapseWindow()
        }
    }

    fun showEdge() {
        if (overlayView != null) return
        screenHeightPx = context.resources.displayMetrics.heightPixels
        screenWidthPx = context.resources.displayMetrics.widthPixels
        val params = createLayoutParams()
        val view = EdgeGestureOverlayView(
            context = overlayContext,
            side = side,
            appRepository = appRepository,
            onSessionStartCallback = {
                overlayView?.setPreviewMode(false)
                expandWindow()
            },
            onSessionEndCallback = {
                if (overlayView?.isSessionActive() != true) {
                    if (previewMode) {
                        overlayView?.setPreviewMode(true, previewContent)
                        expandPreviewWindow()
                    } else {
                        collapseWindow()
                    }
                }
            },
        )
        view.applySettings(settings, context.resources.displayMetrics.widthPixels)
        applyTriggerLayout(params)
        runCatching {
            windowManager.addView(view, params)
            overlayView = view
            windowParams = params
            TaskManagerUtil.prefetchRecentTasks()
            preloadApps()
            if (previewMode) {
                view.setPreviewMode(true, previewContent)
                expandPreviewWindow()
            }
        }.onFailure {
            Log.e(TAG, "Failed to show overlay", it)
        }
    }

    fun hideEdge() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        windowParams = null
    }

    fun reloadApps() {
        preloadApps(force = true)
    }

    fun destroy() {
        loadJob?.cancel()
        hideEdge()
    }

    private fun expandWindow() {
        val view = overlayView ?: return
        val params = windowParams ?: return
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0
        applyNormalTouchFlags(params)
        runCatching { windowManager.updateViewLayout(view, params) }
            .onFailure { Log.e(TAG, "Failed to expand overlay window", it) }
    }

    private fun expandPreviewWindow() {
        val view = overlayView ?: return
        val params = windowParams ?: return
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0
        applyPreviewTouchFlags(params)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun collapseWindow() {
        val view = overlayView ?: return
        val params = windowParams ?: return
        applyTriggerLayout(params)
        applyNormalTouchFlags(params)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun applyTriggerLayout(params: WindowManager.LayoutParams) {
        val edgeWidthPx = (settings.interceptWindowWidthDp(side) * density)
            .toInt()
            .coerceAtLeast(dp(16f).toInt())
        val triggerHeightPx = (screenHeightPx * settings.triggerHeightFraction(side))
            .toInt()
            .coerceAtLeast(dp(48f).toInt())
        val triggerTopPx = (screenHeightPx * settings.triggerTopFraction(side)).toInt()
        params.width = edgeWidthPx
        params.height = triggerHeightPx
        params.x = 0
        params.y = triggerTopPx.coerceIn(0, (screenHeightPx - triggerHeightPx).coerceAtLeast(0))
        params.gravity = when (side) {
            PanelSide.LEFT -> Gravity.TOP or Gravity.START
            PanelSide.RIGHT -> Gravity.TOP or Gravity.END
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).also { applyNormalTouchFlags(it) }
    }

    private fun applyNormalTouchFlags(params: WindowManager.LayoutParams) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    private fun applyPreviewTouchFlags(params: WindowManager.LayoutParams) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    private fun preloadApps(force: Boolean = false) {
        if (!force) {
            val cached = appRepository.getCachedApps()
                .filter { it.packageName !in settings.hiddenAppPackages }
            if (cached.isNotEmpty()) {
                overlayView?.setApps(cached)
            }
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            val apps = appRepository.loadApps(force = force)
                .filter { it.packageName !in settings.hiddenAppPackages }
            overlayView?.setApps(apps)
        }
    }

    private fun dp(value: Float): Float = value * density

    companion object {
        private const val TAG = "SideOverlayController"
    }
}
