package com.slideindex.app.overlay

import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.slideindex.app.gesture.CollapsedWindowBounds
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.util.OverlayBrightnessControl

internal class SideOverlayWindowManager(
    private val ctrl: SideOverlayController,
) {
    private val context get() = ctrl.context
    private val side get() = ctrl.side
    private val windowManager get() = ctrl.androidWindowManager
    private val overlayContext get() = ctrl.overlayContext
    private val renderer get() = ctrl.renderer

    internal var presentationView: EdgeGestureOverlayView? = null
    internal var presentationContainer: FrameLayout? = null
    internal var presentationParams: WindowManager.LayoutParams? = null
    internal var presentationAttached = false
    internal val touchCaptureWindows = mutableListOf<CaptureWindow>()
    internal val exclusionWindows = mutableListOf<CaptureWindow>()
    internal var edgeOverlayDetached = false

    internal var overlayBrightnessFraction: Float? = null
    private var lastOverlayBrightnessApplyMs = 0L
    private var pendingOverlayBrightnessFraction: Float? = null
    private var overlayBrightnessApplyRunnable: Runnable? = null
    private val overlayBrightnessHandler = android.os.Handler(android.os.Looper.getMainLooper())

    internal val overlayBrightness = OverlayBrightnessControl { fraction ->
        applyOverlayWindowBrightness(fraction)
    }

    fun presentationRoot(): View? = presentationContainer

    fun ensurePresentationAttached() {
        if (presentationAttached || overlayLayoutSuspended()) return
        val root = presentationRoot() ?: return
        val content = presentationView ?: return
        val params = presentationParams ?: return
        applyFullScreenPresentationLayout(params)
        applyPresentationTouchFlags(content, params)
        runCatching { windowManager.addView(root, params) }
            .onSuccess { presentationAttached = true }
            .onFailure { Log.e(TAG, "Failed to attach presentation overlay", it) }
    }

    fun detachPresentationWindow() {
        if (!presentationAttached) return
        presentationRoot()?.let { runCatching { windowManager.removeView(it) } }
        presentationAttached = false
    }

    fun detachPresentationIfIdle() {
        if (!presentationAttached || overlayLayoutSuspended()) return
        val view = presentationView ?: return
        if (ctrl.previewMode) return
        if (view.isSessionActive() || view.keepsOverlayExpanded()) return
        detachPresentationWindow()
    }

    fun syncCaptureWindowLayout() {
        val presentation = presentationView ?: return
        if (overlayLayoutSuspended()) return
        syncCaptureWindows(presentation)
        ctrl.syncRuntimeVisuals()
    }

    fun syncPresentationTouchState() {
        if (overlayLayoutSuspended()) return
        val content = presentationView ?: return
        val root = presentationRoot() ?: return
        val params = presentationParams ?: return
        if (content.presentationShouldPassthroughTouches()) {
            if (content.needsPresentationDirectTouch()) {
                if (!presentationAttached) {
                    ensurePresentationAttached()
                } else {
                    applyFullScreenPresentationLayout(params)
                    applyPresentationPassthroughFlags(params)
                    runCatching { windowManager.updateViewLayout(root, params) }
                        .onFailure { Log.e(TAG, "Failed to sync presentation passthrough", it) }
                }
            } else if (presentationAttached) {
                detachPresentationWindow()
            }
            syncCaptureWindows(content)
            content.syncOverlayDialogZOrder()
            return
        }
        if (!presentationAttached) {
            if (ctrl.previewMode) {
                ensurePresentationAttached()
                if (presentationAttached) {
                    renderer.applyPreviewPresentationWindow()
                }
                return
            }
            if (!content.needsPresentationDirectTouch()) return
            ensurePresentationAttached()
            if (!presentationAttached) return
        }
        if (ctrl.previewMode) {
            renderer.applyPreviewPresentationWindow()
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

    fun suspendEdgeOverlay() {
        if (edgeOverlayDetached) return
        detachPresentationWindow()
        touchCaptureWindows.forEach { slot ->
            runCatching { windowManager.removeView(slot.view) }
        }
        renderer.detachAllTriggerVisualWindows()
        exclusionWindows.forEach { slot ->
            runCatching { windowManager.removeView(slot.view) }
        }
        edgeOverlayDetached = true
    }

    fun resumeEdgeOverlay() {
        if (!edgeOverlayDetached) return
        if (OverlayTrampolineGuard.blocksOverlayResume()) return
        if (touchCaptureWindows.isEmpty() && renderer.triggerVisualWindows.isEmpty()) return
        touchCaptureWindows.forEach { slot ->
            runCatching { windowManager.addView(slot.view, slot.params) }
                .onFailure { Log.e(TAG, "Failed to resume capture overlay", it) }
        }
        if (ctrl.shouldShowRuntimeVisuals()) {
            renderer.resumeTriggerVisualWindows()
        }
        exclusionWindows.forEach { slot ->
            runCatching { windowManager.addView(slot.view, slot.params) }
                .onFailure { Log.e(TAG, "Failed to resume exclusion overlay", it) }
        }
        if (ctrl.previewMode || presentationView?.keepsOverlayExpanded() == true) {
            ensurePresentationAttached()
        }
        syncPresentationTouchState()
        edgeOverlayDetached = false
    }

    fun detachTouchCaptureWindows() {
        detachTouchCaptureViewsOnly()
        touchCaptureWindows.clear()
    }

    fun detachTouchCaptureViewsOnly() {
        touchCaptureWindows.forEach { slot ->
            runCatching { windowManager.removeView(slot.view) }
        }
    }

    fun reattachCaptureWindows() {
        if (overlayLayoutSuspended()) return
        touchCaptureWindows.forEach { slot ->
            runCatching { windowManager.addView(slot.view, slot.params) }
                .onFailure { Log.e(TAG, "Failed to reattach capture overlay", it) }
        }
        if (ctrl.shouldShowRuntimeVisuals()) {
            renderer.resumeTriggerVisualWindows()
        }
        exclusionWindows.forEach { slot ->
            runCatching { windowManager.addView(slot.view, slot.params) }
                .onFailure { Log.e(TAG, "Failed to reattach exclusion overlay", it) }
        }
    }

    fun detachAllCaptureWindows() {
        detachTouchCaptureWindows()
        renderer.detachAllTriggerVisualWindows()
        detachAllExclusionWindows()
    }

    fun detachAllExclusionWindows() {
        detachExclusionViewsOnly()
        exclusionWindows.clear()
    }

    fun detachExclusionViewsOnly() {
        exclusionWindows.forEach { slot ->
            runCatching { windowManager.removeView(slot.view) }
        }
    }

    fun attachCaptureWindows(presentation: EdgeGestureOverlayView) {
        if (overlayLayoutSuspended()) return
        val touchHandler: (android.view.MotionEvent) -> Boolean = { event ->
            presentation.handleOverlayTouch(event)
        }
        computeCaptureWindowBounds().forEachIndexed { index, bounds ->
            val params = createCaptureLayoutParams()
            applyCaptureLayout(params, bounds)
            val capture = EdgeTouchCaptureView(overlayContext, side, index, touchHandler)
            windowManager.addView(capture, params)
            touchCaptureWindows += CaptureWindow(capture, params)
        }
        if (ctrl.shouldShowRuntimeVisuals()) {
            renderer.attachTriggerVisualWindows()
        }
        attachExclusionWindows()
    }

    fun attachExclusionWindows() {
        computeSystemGestureExclusionBounds().forEach { bounds ->
            val params = createCaptureLayoutParams()
            applyCaptureLayout(params, bounds)
            OverlayWindowTypes.applyExclusionPassthroughFlags(params)
            val exclusion = EdgeSystemGestureExclusionView(overlayContext)
            windowManager.addView(exclusion, params)
            exclusionWindows += CaptureWindow(exclusion, params)
        }
    }

    fun syncCaptureWindows(presentation: EdgeGestureOverlayView) {
        if (overlayLayoutSuspended()) return
        if (presentation.presentationShouldPassthroughTouches()) {
            detachTouchCaptureViewsOnly()
            detachExclusionViewsOnly()
            presentation.syncOverlayDialogZOrder()
            return
        }
        syncTouchCaptureWindows(presentation)
        syncExclusionWindows()
    }

    fun setPresentationFocusable(focusable: Boolean) {
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

    fun createPresentationLayoutParams(): WindowManager.LayoutParams =
        OverlayWindowTypes.createPresentationParams(context)

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
                val capture = EdgeTouchCaptureView(overlayContext, side, index, touchHandler)
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
        if (overlayLayoutSuspended()) return
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

    private fun computeCaptureWindowBounds(): List<CollapsedWindowBounds> =
        GestureZoneLayout.computeCaptureWindowBounds(
            settings = ctrl.settings,
            side = side,
            screenHeightPx = ctrl.screenHeightPx,
            density = ctrl.density,
        )

    private fun computeSystemGestureExclusionBounds(): List<CollapsedWindowBounds> =
        GestureZoneLayout.computeSystemGestureExclusionBounds(
            settings = ctrl.settings,
            side = side,
            screenHeightPx = ctrl.screenHeightPx,
            density = ctrl.density,
        )

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

    private fun applyFullScreenPresentationLayout(params: WindowManager.LayoutParams) {
        OverlayWindowTypes.applyFullScreen(params)
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

    private fun applyCaptureTouchFlags(params: WindowManager.LayoutParams) {
        OverlayWindowTypes.applyCaptureTouchFlags(params)
    }

    private fun applyPresentationPassthroughFlags(params: WindowManager.LayoutParams) {
        OverlayWindowTypes.applyPresentationPassthroughFlags(params)
    }

    private fun applyPresentationInteractiveFlags(params: WindowManager.LayoutParams) {
        OverlayWindowTypes.applyPresentationInteractiveFlags(params)
    }

    internal fun overlayLayoutSuspended(): Boolean =
        edgeOverlayDetached || OverlayTrampolineGuard.blocksOverlayPresentationTouch()

    internal data class CaptureWindow(
        val view: View,
        var params: WindowManager.LayoutParams,
    )

    companion object {
        private const val TAG = "SideOverlayController"
        private const val MIN_OVERLAY_BRIGHTNESS = 0.01f
        private const val OVERLAY_BRIGHTNESS_MIN_INTERVAL_MS = 32L
    }
}
