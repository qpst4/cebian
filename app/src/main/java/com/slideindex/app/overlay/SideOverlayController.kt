package com.slideindex.app.overlay

import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.CollapsedWindowBounds
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.overlay.animation.GestureAnimationOverlayRegistry
import com.slideindex.app.util.OverlayBrightnessControl
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Dual-window edge overlay per side:
 * - [EdgeTouchCaptureView]: fixed edge strip, always attached (touch capture)
 * - [EdgeGestureOverlayView]: full-screen presentation, attached only while drawing / panels
 *
 * Idle state keeps **capture strips only** so the screen center is never blocked.
 */
class SideOverlayController(
    private val context: android.content.Context,
    val side: PanelSide,
    private val windowManager: WindowManager,
    private val appRepository: AppRepository,
    private val scope: CoroutineScope,
    private val clickPassthroughHandler: ((Float, Float, () -> Unit) -> Unit)? = null,
    private val onShellCommandsPersist: (List<ShellCommand>) -> Unit = {},
    private val onQuickLauncherItemsPersist: (List<QuickLauncherItem>) -> Unit = {},
    private val onComposeOverlayDialogStateChanged: () -> Unit = {},
) {
    private var settings: AppSettings = AppSettings()
    private var screenWidthPx: Int = 0
    private var screenHeightPx: Int = 0
    private var previewMode = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY

    private val overlayContext = OverlayCompose.themedContext(context)
    internal val overlayPresentation: EdgeGestureOverlayView?
        get() = presentationView
    private var presentationView: EdgeGestureOverlayView? = null
    private var presentationContainer: FrameLayout? = null
    private var presentationParams: WindowManager.LayoutParams? = null
    private var presentationAttached = false
    private val touchCaptureWindows = mutableListOf<CaptureWindow>()
    private val exclusionWindows = mutableListOf<CaptureWindow>()
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
        screenWidthPx = screenWidth
        screenHeightPx = context.resources.displayMetrics.heightPixels
        presentationView?.applySettings(newSettings, screenWidth)
        if (presentationView != null) {
            preloadApps(force = hiddenChanged)
        }
        syncCaptureWindowLayout()
        if (previewMode) {
            presentationView?.invalidate()
        }
    }

    fun forceCollapseIfIdle() {
        val view = presentationView ?: return
        if (view.isSessionActive() || previewMode) return
        view.forceRecoverInteractionState()
        detachPresentationIfIdle()
    }

    fun setPreviewMode(enabled: Boolean, content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY) {
        val changed = previewMode != enabled || previewContent != content
        if (!changed) return
        previewMode = enabled
        previewContent = content
        val view = presentationView ?: return
        view.setPreviewMode(enabled, content)
        if (enabled) {
            ensurePresentationAttached()
            applyPreviewPresentationWindow()
        } else {
            detachPresentationIfIdle()
        }
    }

    fun showEdge() {
        if (touchCaptureWindows.isNotEmpty()) return
        screenWidthPx = context.resources.displayMetrics.widthPixels
        screenHeightPx = context.resources.displayMetrics.heightPixels

        val container = FrameLayout(overlayContext)
        val presentation = EdgeGestureOverlayView(
            context = overlayContext,
            side = side,
            appRepository = appRepository,
            onSessionStartCallback = {
                presentationView?.setPreviewMode(false)
                ensurePresentationAttached()
                syncPresentationTouchState()
                syncCaptureWindowLayout()
            },
            onSessionEndCallback = {
                presentationView?.forceRecoverInteractionState()
                if (presentationView?.keepsOverlayExpanded() != true &&
                    presentationView?.isSessionActive() != true
                ) {
                    if (previewMode) {
                        presentationView?.setPreviewMode(true, previewContent)
                        ensurePresentationAttached()
                        applyPreviewPresentationWindow()
                    } else {
                        detachPresentationIfIdle()
                    }
                } else {
                    syncPresentationTouchState()
                }
                syncCaptureWindowLayout()
            },
            onGestureTrackingStartCallback = {
                ensurePresentationAttached()
                syncPresentationTouchState()
                TaskManagerUtil.ensureServiceBound()
            },
            onAdjustPanelLayoutCallback = { _ ->
                presentationView?.setPreviewMode(false)
                presentationView?.applyAdjustPanelOverlayLayout()
                ensurePresentationAttached()
                syncPresentationTouchState()
            },
            onAdjustPanelDismissCallback = {
                syncPresentationTouchState()
                detachPresentationIfIdle()
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
            onShellPanelFocusChange = { focusable -> setPresentationFocusable(focusable) },
            onOverlayWindowSuspend = { suspendEdgeOverlay() },
            onOverlayWindowResume = { resumeEdgeOverlay() },
            overlayBrightness = OverlayBrightnessControl { fraction ->
                applyOverlayWindowBrightness(fraction)
            },
        ).also { view ->
            view.onPresentationTouchRequirementChanged = {
                if (view.needsPresentationDirectTouch() && !view.presentationShouldPassthroughTouches()) {
                    ensurePresentationAttached()
                }
                syncPresentationTouchState()
                syncCaptureWindowLayout()
                if (view.presentationShouldPassthroughTouches()) {
                    view.syncOverlayDialogZOrder()
                }
                detachPresentationIfIdle()
                onComposeOverlayDialogStateChanged()
            }
        }

        container.addView(
            presentation,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        GestureAnimationOverlayRegistry.controller(side).attach(container, overlayContext)

        val params = createPresentationLayoutParams()
        applyFullScreenPresentationLayout(params)
        presentation.applySettings(settings, screenWidthPx)
        presentation.applyExpandedOverlayLayout()

        runCatching {
            attachCaptureWindows(presentation)
            presentationView = presentation
            presentationContainer = container
            presentationParams = params
            presentationAttached = false
            TaskManagerUtil.ensureServiceBound()
            preloadApps()
            if (previewMode) {
                presentation.setPreviewMode(true, previewContent)
                ensurePresentationAttached()
                applyPreviewPresentationWindow()
            }
        }.onFailure {
            Log.e(TAG, "Failed to show overlay", it)
            GestureAnimationOverlayRegistry.controller(side).detach()
            detachAllCaptureWindows()
            presentationView = null
            presentationContainer = null
            presentationParams = null
            presentationAttached = false
        }
    }

    fun hideEdge() {
        detachPresentationWindow()
        detachAllCaptureWindows()
        GestureAnimationOverlayRegistry.controller(side).detach()
        presentationView = null
        presentationContainer = null
        presentationParams = null
        presentationAttached = false
        edgeOverlayDetached = false
    }

    fun suspendEdgeOverlay() {
        if (edgeOverlayDetached) return
        detachPresentationWindow()
        touchCaptureWindows.forEach { slot ->
            runCatching { windowManager.removeView(slot.view) }
        }
        exclusionWindows.forEach { slot ->
            runCatching { windowManager.removeView(slot.view) }
        }
        edgeOverlayDetached = true
    }

    fun resumeEdgeOverlay() {
        if (!edgeOverlayDetached) return
        if (touchCaptureWindows.isEmpty()) return
        touchCaptureWindows.forEach { slot ->
            runCatching { windowManager.addView(slot.view, slot.params) }
                .onFailure { Log.e(TAG, "Failed to resume capture overlay", it) }
        }
        exclusionWindows.forEach { slot ->
            runCatching { windowManager.addView(slot.view, slot.params) }
                .onFailure { Log.e(TAG, "Failed to resume exclusion overlay", it) }
        }
        if (previewMode || presentationView?.keepsOverlayExpanded() == true) {
            ensurePresentationAttached()
        }
        syncPresentationTouchState()
        edgeOverlayDetached = false
    }

    fun suspendCapturesForComposeDialog() {
        detachAllCaptureWindows()
        presentationView?.syncOverlayDialogZOrder()
    }

    fun reloadApps() {
        preloadApps(force = true)
    }

    fun destroy() {
        loadJob?.cancel()
        hideEdge()
    }

    private fun presentationRoot(): View? = presentationContainer

    private fun ensurePresentationAttached() {
        if (presentationAttached || edgeOverlayDetached) return
        val root = presentationRoot() ?: return
        val content = presentationView ?: return
        val params = presentationParams ?: return
        applyFullScreenPresentationLayout(params)
        applyPresentationTouchFlags(content, params)
        runCatching { windowManager.addView(root, params) }
            .onSuccess { presentationAttached = true }
            .onFailure { Log.e(TAG, "Failed to attach presentation overlay", it) }
    }

    private fun detachPresentationWindow() {
        if (!presentationAttached) return
        presentationRoot()?.let { runCatching { windowManager.removeView(it) } }
        presentationAttached = false
    }

    private fun detachPresentationIfIdle() {
        if (!presentationAttached || edgeOverlayDetached) return
        val view = presentationView ?: return
        if (previewMode) return
        if (view.isSessionActive() || view.keepsOverlayExpanded()) return
        detachPresentationWindow()
    }

    private fun syncCaptureWindowLayout() {
        val presentation = presentationView ?: return
        if (edgeOverlayDetached) return
        syncCaptureWindows(presentation)
    }

    private fun syncPresentationTouchState() {
        val content = presentationView ?: return
        val root = presentationRoot() ?: return
        val params = presentationParams ?: return
        if (content.presentationShouldPassthroughTouches()) {
            detachPresentationWindow()
            syncCaptureWindows(content)
            return
        }
        if (!presentationAttached) {
            if (!content.needsPresentationDirectTouch()) return
            ensurePresentationAttached()
            if (!presentationAttached) return
        }
        if (previewMode) {
            applyPreviewPresentationWindow()
            return
        }
        applyFullScreenPresentationLayout(params)
        applyPresentationTouchFlags(content, params)
        params.screenBrightness = when (overlayBrightnessFraction) {
            null -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            else -> overlayBrightnessFraction!!.coerceIn(MIN_OVERLAY_BRIGHTNESS, 1f)
        }
        runCatching { windowManager.updateViewLayout(root, params) }
            .onFailure { Log.e(TAG, "Failed to sync presentation touch state", it) }
    }

    private fun applyPresentationTouchFlags(
        view: EdgeGestureOverlayView,
        params: WindowManager.LayoutParams,
    ) {
        if (view.needsPresentationDirectTouch()) {
            if (view.presentationShouldPassthroughTouches()) {
                applyPresentationPassthroughFlags(params)
            } else {
                applyPresentationInteractiveFlags(params)
            }
        } else {
            applyPresentationPassthroughFlags(params)
        }
    }

    private fun applyPreviewPresentationWindow() {
        if (!presentationAttached) return
        val content = presentationView ?: return
        val root = presentationRoot() ?: return
        val params = presentationParams ?: return
        content.applyExpandedOverlayLayout()
        applyFullScreenPresentationLayout(params)
        applyPreviewPresentationFlags(params)
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    private fun applyFullScreenPresentationLayout(params: WindowManager.LayoutParams) {
        OverlayWindowTypes.applyFullScreen(params)
    }

    private fun computeCaptureWindowBounds(): List<CollapsedWindowBounds> {
        presentationView?.panelInteractionCaptureBounds()?.let { return it }
        return GestureZoneLayout.computeCaptureWindowBounds(
            settings = settings,
            side = side,
            screenHeightPx = screenHeightPx,
            density = density,
        )
    }

    private fun computeSystemGestureExclusionBounds(): List<CollapsedWindowBounds> =
        GestureZoneLayout.computeSystemGestureExclusionBounds(
            settings = settings,
            side = side,
            screenHeightPx = screenHeightPx,
            density = density,
        )

    private fun attachCaptureWindows(presentation: EdgeGestureOverlayView) {
        val touchHandler: (android.view.MotionEvent) -> Boolean = { event ->
            presentation.handleOverlayTouch(event)
        }
        computeCaptureWindowBounds().forEach { bounds ->
            val params = createCaptureLayoutParams()
            applyCaptureLayout(params, bounds)
            val capture = EdgeTouchCaptureView(overlayContext, touchHandler)
            windowManager.addView(capture, params)
            touchCaptureWindows += CaptureWindow(capture, params)
        }
        attachExclusionWindows()
    }

    private fun attachExclusionWindows() {
        computeSystemGestureExclusionBounds().forEach { bounds ->
            val params = createCaptureLayoutParams()
            applyCaptureLayout(params, bounds)
            OverlayWindowTypes.applyExclusionPassthroughFlags(params)
            val exclusion = EdgeSystemGestureExclusionView(overlayContext)
            windowManager.addView(exclusion, params)
            exclusionWindows += CaptureWindow(exclusion, params)
        }
    }

    private fun syncCaptureWindows(presentation: EdgeGestureOverlayView) {
        if (presentation.presentationShouldPassthroughTouches()) {
            detachAllCaptureWindows()
            detachAllExclusionWindows()
            presentation.syncOverlayDialogZOrder()
            return
        }
        syncTouchCaptureWindows(presentation)
        syncExclusionWindows()
    }

    private fun syncTouchCaptureWindows(presentation: EdgeGestureOverlayView) {
        val bounds = computeCaptureWindowBounds()
        val touchHandler: (android.view.MotionEvent) -> Boolean = { event ->
            presentation.handleOverlayTouch(event)
        }
        val passthrough = presentation.presentationShouldPassthroughTouches()
        while (touchCaptureWindows.size > bounds.size) {
            val slot = touchCaptureWindows.removeAt(touchCaptureWindows.lastIndex)
            runCatching { windowManager.removeView(slot.view) }
                .onFailure { Log.e(TAG, "Failed to remove capture window", it) }
        }
        bounds.forEachIndexed { index, bound ->
            if (index >= touchCaptureWindows.size) {
                val params = createCaptureLayoutParams()
                applyCaptureLayout(params, bound)
                if (passthrough) {
                    applyPresentationPassthroughFlags(params)
                } else {
                    applyCaptureTouchFlags(params)
                }
                val capture = EdgeTouchCaptureView(overlayContext, touchHandler)
                runCatching { windowManager.addView(capture, params) }
                    .onSuccess { touchCaptureWindows += CaptureWindow(capture, params) }
                    .onFailure { Log.e(TAG, "Failed to add capture window", it) }
            } else {
                val slot = touchCaptureWindows[index]
                applyCaptureLayout(slot.params, bound)
                if (passthrough) {
                    applyPresentationPassthroughFlags(slot.params)
                } else {
                    applyCaptureTouchFlags(slot.params)
                }
                runCatching { windowManager.updateViewLayout(slot.view, slot.params) }
                    .onFailure { Log.e(TAG, "Failed to sync capture window layout", it) }
            }
        }
    }

    private fun syncExclusionWindows() {
        val bounds = computeSystemGestureExclusionBounds()
        while (exclusionWindows.size > bounds.size) {
            val slot = exclusionWindows.removeAt(exclusionWindows.lastIndex)
            runCatching { windowManager.removeView(slot.view) }
                .onFailure { Log.e(TAG, "Failed to remove exclusion window", it) }
        }
        bounds.forEachIndexed { index, bound ->
            if (index >= exclusionWindows.size) {
                val params = createCaptureLayoutParams()
                applyCaptureLayout(params, bound)
                OverlayWindowTypes.applyExclusionPassthroughFlags(params)
                val exclusion = EdgeSystemGestureExclusionView(overlayContext)
                runCatching { windowManager.addView(exclusion, params) }
                    .onSuccess { exclusionWindows += CaptureWindow(exclusion, params) }
                    .onFailure { Log.e(TAG, "Failed to add exclusion window", it) }
            } else {
                val slot = exclusionWindows[index]
                applyCaptureLayout(slot.params, bound)
                OverlayWindowTypes.applyExclusionPassthroughFlags(slot.params)
                runCatching { windowManager.updateViewLayout(slot.view, slot.params) }
                    .onFailure { Log.e(TAG, "Failed to sync exclusion window layout", it) }
            }
        }
    }

    private fun detachAllCaptureWindows() {
        touchCaptureWindows.forEach { slot ->
            runCatching { windowManager.removeView(slot.view) }
        }
        touchCaptureWindows.clear()
        detachAllExclusionWindows()
    }

    private fun detachAllExclusionWindows() {
        exclusionWindows.forEach { slot ->
            runCatching { windowManager.removeView(slot.view) }
        }
        exclusionWindows.clear()
    }

    private fun applyCaptureLayout(
        params: WindowManager.LayoutParams,
        bounds: CollapsedWindowBounds,
    ) {
        params.width = bounds.widthPx
        params.height = bounds.heightPx
        params.x = 0
        params.y = bounds.yPx
        params.gravity = windowGravity()
    }

    private fun windowGravity(): Int = when (side) {
        PanelSide.LEFT -> Gravity.TOP or Gravity.START
        PanelSide.RIGHT -> Gravity.TOP or Gravity.END
    }

    private fun createCaptureLayoutParams(): WindowManager.LayoutParams =
        OverlayWindowTypes.createCaptureParams(context)

    private fun createPresentationLayoutParams(): WindowManager.LayoutParams =
        OverlayWindowTypes.createPresentationParams(context)

    private fun applyCaptureTouchFlags(params: WindowManager.LayoutParams) {
        OverlayWindowTypes.applyCaptureTouchFlags(params)
    }

    private fun applyPresentationPassthroughFlags(params: WindowManager.LayoutParams) {
        OverlayWindowTypes.applyPresentationPassthroughFlags(params)
    }

    private fun applyPresentationInteractiveFlags(params: WindowManager.LayoutParams) {
        OverlayWindowTypes.applyPresentationInteractiveFlags(params)
    }

    private fun applyPreviewPresentationFlags(params: WindowManager.LayoutParams) {
        OverlayWindowTypes.applyPreviewPresentationFlags(params)
    }

    private fun preloadApps(force: Boolean = false) {
        if (!force) {
            val cached = appRepository.getCachedApps()
                .filter { it.packageName !in settings.hiddenAppPackages }
            if (cached.isNotEmpty()) {
                presentationView?.setApps(cached)
            }
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            val apps = appRepository.loadApps(force = force)
                .filter { it.packageName !in settings.hiddenAppPackages }
            presentationView?.setApps(apps)
        }
    }

    private fun setPresentationFocusable(focusable: Boolean) {
        if (!presentationAttached) return
        val view = presentationView ?: return
        val root = presentationRoot() ?: return
        val params = presentationParams ?: return
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            view.clearFocus()
        }
        runCatching { windowManager.updateViewLayout(root, params) }
            .onFailure { Log.e(TAG, "Failed to update presentation focus", it) }
        if (focusable) {
            view.isFocusableInTouchMode = true
            view.requestFocus()
        }
    }

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
        if (fraction != null) {
            ensurePresentationAttached()
        }
        syncPresentationTouchState()
        if (presentationView?.presentationShouldPassthroughTouches() == true) {
            presentationView?.syncOverlayDialogZOrder()
        }
        if (fraction == null) {
            detachPresentationIfIdle()
        }
    }

    companion object {
        private const val TAG = "SideOverlayController"
        private const val MIN_OVERLAY_BRIGHTNESS = 0.01f
        private const val OVERLAY_BRIGHTNESS_MIN_INTERVAL_MS = 32L

        private data class CaptureWindow(
            val view: android.view.View,
            var params: WindowManager.LayoutParams,
        )
    }
}
