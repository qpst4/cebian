package com.slideindex.app.overlay

import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.slideindex.app.data.AppRepository
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.edgeTriggerWidthDp
import com.slideindex.app.settings.interceptWindowWidthDp
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.OverlayBrightnessControl
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SideOverlayController(
    private val context: android.content.Context,
    val side: PanelSide,
    private val windowManager: WindowManager,
    private val appRepository: AppRepository,
    private val scope: CoroutineScope,
    private val clickPassthroughHandler: ((Float, Float, () -> Unit) -> Unit)? = null,
    private val onShellCommandsPersist: (List<ShellCommand>) -> Unit = {},
    private val onQuickLauncherItemsPersist: (List<QuickLauncherItem>) -> Unit = {},
) {
    private var settings: AppSettings = AppSettings()
    private var screenHeightPx: Int = 0
    private var previewMode = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY

    private val overlayContext = OverlayCompose.themedContext(context)
    private var overlayView: EdgeGestureOverlayView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var edgeOverlayDetached = false
    private var loadJob: Job? = null
    private var overlayBrightnessFraction: Float? = null
    private var lastOverlayBrightnessApplyMs = 0L
    private var pendingOverlayBrightnessFraction: Float? = null
    private var overlayBrightnessApplyRunnable: Runnable? = null
    private val overlayBrightnessHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val density get() = context.resources.displayMetrics.density

    fun updateSettings(newSettings: AppSettings, screenWidth: Int) {
        val hiddenChanged = newSettings.hiddenAppPackages != settings.hiddenAppPackages
        settings = newSettings
        screenHeightPx = context.resources.displayMetrics.heightPixels
        overlayView?.applySettings(newSettings, screenWidth)
        if (overlayView != null) {
            preloadApps(force = hiddenChanged)
        }
        if (overlayView != null && windowParams != null &&
            overlayView?.isSessionActive() != true &&
            !previewMode
        ) {
            forceCollapseIfIdle()
        } else if (previewMode) {
            overlayView?.invalidate()
        }
    }

    fun forceCollapseIfIdle() {
        val view = overlayView ?: return
        if (view.isSessionActive() || previewMode) return
        view.forceRecoverInteractionState()
        collapseWindow()
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
                overlayView?.forceRecoverInteractionState()
                if (overlayView?.keepsOverlayExpanded() != true && overlayView?.isSessionActive() != true) {
                    if (previewMode) {
                        overlayView?.setPreviewMode(true, previewContent)
                        expandPreviewWindow()
                    } else {
                        collapseWindow()
                    }
                }
            },
            onGestureTrackingStartCallback = {
                overlayView?.setPreviewMode(false)
                expandWindow()
                TaskManagerUtil.ensureServiceBound()
            },
            onAdjustPanelLayoutCallback = { anchorRawY ->
                overlayView?.setPreviewMode(false)
                layoutPostReleaseAdjustWindow(anchorRawY)
            },
            onAdjustPanelDismissCallback = {
                collapseWindow()
            },
            onClickPassthroughCallback = { rawX, rawY, onComplete ->
                val handler = clickPassthroughHandler
                if (handler != null) {
                    handler(rawX, rawY, onComplete)
                } else {
                    onComplete()
                }
            },
            onShellCommandsPersist = onShellCommandsPersist,
            onQuickLauncherItemsPersist = onQuickLauncherItemsPersist,
            onShellPanelFocusChange = { focusable -> setOverlayFocusable(focusable) },
            onOverlayWindowSuspend = { suspendEdgeOverlay() },
            onOverlayWindowResume = { resumeEdgeOverlay() },
            overlayBrightness = OverlayBrightnessControl { fraction ->
                applyOverlayWindowBrightness(fraction)
            },
        )
        view.applySettings(settings, context.resources.displayMetrics.widthPixels)
        applyTriggerLayout(params)
        runCatching {
            windowManager.addView(view, params)
            overlayView = view
            windowParams = params
            TaskManagerUtil.ensureServiceBound()
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
        edgeOverlayDetached = false
    }

    /** Temporarily detach the edge overlay window without destroying session state. */
    fun suspendEdgeOverlay() {
        val view = overlayView ?: return
        if (edgeOverlayDetached) return
        runCatching { windowManager.removeView(view) }
            .onFailure { Log.e(TAG, "Failed to suspend edge overlay", it) }
        edgeOverlayDetached = true
    }

    fun resumeEdgeOverlay() {
        val view = overlayView ?: return
        val params = windowParams ?: return
        if (!edgeOverlayDetached) return
        runCatching { windowManager.addView(view, params) }
            .onFailure { Log.e(TAG, "Failed to resume edge overlay", it) }
        edgeOverlayDetached = false
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
        params.gravity = expandedWindowGravity()
        params.x = 0
        params.y = 0
        applyNormalTouchFlags(params)
        runCatching { windowManager.updateViewLayout(view, params) }
            .onFailure { Log.e(TAG, "Failed to expand overlay window", it) }
    }

    private fun layoutPostReleaseAdjustWindow(anchorRawY: Float) {
        val view = overlayView ?: return
        val params = windowParams ?: return
        params.width = AdjustLevelIndicator.panelWindowWidthPx(density)
        params.height = screenHeightPx
        params.gravity = expandedWindowGravity()
        params.x = 0
        params.y = 0
        applyNormalTouchFlags(params)
        runCatching { windowManager.updateViewLayout(view, params) }
            .onFailure { Log.e(TAG, "Failed to layout post-release adjust window", it) }
    }

    private fun expandPreviewWindow() {
        val view = overlayView ?: return
        val params = windowParams ?: return
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.gravity = expandedWindowGravity()
        params.x = 0
        params.y = 0
        applyPreviewTouchFlags(params)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun expandedWindowGravity(): Int = when (side) {
        PanelSide.LEFT -> Gravity.TOP or Gravity.START
        PanelSide.RIGHT -> Gravity.TOP or Gravity.END
    }

    private fun collapseWindow() {
        val view = overlayView ?: return
        val params = windowParams ?: return
        applyTriggerLayout(params)
        applyNormalTouchFlags(params)
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        overlayBrightnessFraction = null
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun applyTriggerLayout(params: WindowManager.LayoutParams) {
        val edgeWidthPx = (settings.interceptWindowWidthDp(side) * density)
            .toInt()
            .coerceAtLeast(dp(16f).toInt())
        params.width = edgeWidthPx
        params.height = screenHeightPx
        params.x = 0
        params.y = 0
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

    private fun setOverlayFocusable(focusable: Boolean) {
        val view = overlayView ?: return
        val params = windowParams ?: return
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            view.clearFocus()
        }
        runCatching { windowManager.updateViewLayout(view, params) }
            .onFailure { Log.e(TAG, "Failed to update overlay focus", it) }
        if (focusable) {
            view.isFocusableInTouchMode = true
            view.requestFocus()
        }
    }

    private fun dp(value: Float): Float = value * density

    private fun applyOverlayWindowBrightness(fraction: Float?) {
        if (overlayBrightnessFraction == fraction) return
        pendingOverlayBrightnessFraction = fraction
        val now = android.os.SystemClock.uptimeMillis()
        val elapsed = now - lastOverlayBrightnessApplyMs
        if (elapsed >= OVERLAY_BRIGHTNESS_MIN_INTERVAL_MS) {
            flushOverlayWindowBrightness()
            return
        }
        overlayBrightnessApplyRunnable?.let { overlayBrightnessHandler.removeCallbacks(it) }
        val runnable = Runnable {
            overlayBrightnessApplyRunnable = null
            flushOverlayWindowBrightness()
        }
        overlayBrightnessApplyRunnable = runnable
        overlayBrightnessHandler.postDelayed(
            runnable,
            OVERLAY_BRIGHTNESS_MIN_INTERVAL_MS - elapsed,
        )
    }

    private fun flushOverlayWindowBrightness() {
        val fraction = pendingOverlayBrightnessFraction
        if (overlayBrightnessFraction == fraction) return
        overlayBrightnessFraction = fraction
        lastOverlayBrightnessApplyMs = android.os.SystemClock.uptimeMillis()
        val view = overlayView ?: return
        val params = windowParams ?: return
        params.screenBrightness = when (fraction) {
            null -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            else -> fraction.coerceIn(MIN_OVERLAY_BRIGHTNESS, 1f)
        }
        runCatching { windowManager.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "Failed to update overlay brightness", it) }
    }

    companion object {
        private const val TAG = "SideOverlayController"
        private const val MIN_OVERLAY_BRIGHTNESS = 0.01f
        private const val OVERLAY_BRIGHTNESS_MIN_INTERVAL_MS = 32L
    }
}
