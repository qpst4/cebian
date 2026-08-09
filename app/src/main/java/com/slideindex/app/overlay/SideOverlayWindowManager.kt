package com.slideindex.app.overlay

import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.slideindex.app.gesture.CollapsedWindowBounds
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.overlay.compositor.OverlaySceneController
import com.slideindex.app.settings.triggerHandles
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
    private var capturePassthroughSuspended = false
    private val trackedWmViews = mutableListOf<View>()

    internal fun trackOverlayView(view: View) {
        if (view !in trackedWmViews) {
            trackedWmViews += view
        }
    }

    internal fun untrackOverlayView(view: View) {
        trackedWmViews.remove(view)
    }

    private fun addOverlayView(view: View, params: WindowManager.LayoutParams) {
        OverlayWindowTypes.ensureNoBrightnessOverride(params)
        windowManager.addView(view, params)
        trackOverlayView(view)
    }

    private fun removeOverlayView(view: View) {
        untrackOverlayView(view)
        runCatching { windowManager.removeView(view) }
    }

    internal fun purgeAllTrackedOverlayViews() {
        trackedWmViews.toList().forEach { view ->
            runCatching { windowManager.removeView(view) }
        }
        trackedWmViews.clear()
    }

    internal fun suspendCaptureTouchForPassthrough() {
        capturePassthroughSuspended = true
        detachPresentationWindow()
        detachTouchCaptureViewsOnly()
        detachExclusionViewsOnly()
    }

    internal fun resumeCaptureTouchAfterPassthrough() {
        if (!capturePassthroughSuspended) return
        capturePassthroughSuspended = false
        if (overlayLayoutSuspended() || edgeOverlayDetached) return
        if (presentationView?.presentationShouldPassthroughTouches() == true) return
        val presentation = presentationView ?: return
        reattachCaptureWindows()
        syncCaptureWindows(presentation, forceLayout = true, applyToWindowManager = true)
        syncPresentationTouchState()
    }

    internal var overlayBrightnessFraction: Float? = null

    internal val overlayBrightness = OverlayBrightnessControl { fraction ->
        applyOverlayWindowBrightness(fraction)
    }

    fun presentationRoot(): View? = presentationContainer

    fun ensurePresentationAttached(forceWhenIdle: Boolean = false) {
        if (overlayLayoutSuspended()) return
        val root = presentationRoot() ?: return
        val content = presentationView ?: return
        val params = presentationParams ?: return
        if (!forceWhenIdle &&
            !ctrl.previewMode &&
            !content.isSessionActive() &&
            !content.needsPresentationDirectTouch()
        ) {
            return
        }
        applyFullScreenPresentationLayout(params)
        clearPresentationBrightnessOverride(params)
        applyPresentationTouchFlags(content, params)
        if (!presentationAttached) {
            runCatching { addOverlayView(root, params) }
                .onSuccess { presentationAttached = true }
                .onFailure { Log.e(TAG, "Failed to attach presentation overlay", it) }
        } else {
            runCatching { windowManager.updateViewLayout(root, params) }
                .onFailure { Log.e(TAG, "Failed to sync presentation overlay", it) }
        }
    }

    fun detachPresentationWindow() {
        if (!presentationAttached) return
        presentationParams?.let { clearPresentationBrightnessOverride(it) }
        presentationRoot()?.let { removeOverlayView(it) }
        presentationAttached = false
    }

    fun detachPresentationUnlessRequired() {
        if (overlayLayoutSuspended()) return
        val view = presentationView ?: return
        if (ctrl.previewMode || view.isSessionActive() || view.needsPresentationDirectTouch() ||
            view.keepsOverlayExpanded()
        ) {
            return
        }
        detachPresentationWindow()
    }

    fun syncCaptureWindowLayout() {
        val presentation = presentationView ?: return
        if (ctrl.settings.triggerHandles(side).isEmpty()) {
            detachAllCaptureWindows()
            return
        }
        syncCaptureWindows(
            presentation = presentation,
            forceLayout = true,
            applyToWindowManager = !edgeOverlayDetached,
        )
        ctrl.syncRuntimeVisuals()
    }

    fun syncPresentationTouchState() {
        if (overlayLayoutSuspended()) return
        val content = presentationView ?: return
        val root = presentationRoot() ?: return
        val params = presentationParams ?: return
        if (content.presentationShouldPassthroughTouches() &&
            content.panelMode() == OverlayPanelMode.NONE
        ) {
            if (content.needsPresentationDirectTouch()) {
                if (!presentationAttached) {
                    ensurePresentationAttached()
                } else {
                    applyFullScreenPresentationLayout(params)
                    applyPresentationPassthroughFlags(params)
                    clearPresentationBrightnessOverride(params)
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
        val edgeOnlyGestureTracking = content.isSessionActive() &&
            content.panelMode() == OverlayPanelMode.NONE &&
            !content.needsPresentationDirectTouch()
        if (edgeOnlyGestureTracking) {
            // 全屏 NOT_TOUCHABLE presentation：承载手势动画 ComposeView（动画挂在 container 上）。
            applyFullScreenPresentationLayout(params)
            applyPresentationPassthroughFlags(params)
            if (!presentationAttached) {
                runCatching { addOverlayView(root, params) }
                    .onSuccess { presentationAttached = true }
                    .onFailure { Log.e(TAG, "Failed to attach presentation for edge gesture", it) }
            } else {
                runCatching { windowManager.updateViewLayout(root, params) }
                    .onFailure { Log.e(TAG, "Failed to sync presentation for edge gesture", it) }
            }
            return
        }
        if (!presentationAttached) {
            if (ctrl.previewMode && content.panelMode() == OverlayPanelMode.NONE) {
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
        if (ctrl.previewMode && content.panelMode() == OverlayPanelMode.NONE) {
            renderer.applyPreviewPresentationWindow()
            return
        }
        applyFullScreenPresentationLayout(params)
        applyPresentationTouchFlags(content, params)
        clearPresentationBrightnessOverride(params)
        runCatching { windowManager.updateViewLayout(root, params) }
            .onFailure { Log.e(TAG, "Failed to sync presentation touch state", it) }
    }

    fun clearOverlayWindowBrightness() {
        overlayBrightnessFraction = null
        presentationParams?.let { OverlayWindowTypes.ensureNoBrightnessOverride(it) }
        touchCaptureWindows.forEach { slot ->
            OverlayWindowTypes.ensureNoBrightnessOverride(slot.params)
        }
    }

    fun suspendEdgeOverlay() {
        if (edgeOverlayDetached) return
        clearOverlayWindowBrightness()
        detachPresentationWindow()
        touchCaptureWindows.forEach { slot ->
            removeOverlayView(slot.view)
        }
        renderer.detachAllTriggerVisualWindows()
        exclusionWindows.forEach { slot ->
            removeOverlayView(slot.view)
        }
        edgeOverlayDetached = true
    }

    fun resumeEdgeOverlay() {
        if (!edgeOverlayDetached) return
        if (OverlayTrampolineGuard.blocksOverlayResume()) return
        presentationView?.let { syncCaptureWindows(it, forceLayout = true, applyToWindowManager = true) }
        if (touchCaptureWindows.isEmpty() && renderer.triggerVisualWindows.isEmpty()) {
            edgeOverlayDetached = false
            return
        }
        touchCaptureWindows.forEach { slot ->
            runCatching { addOverlayView(slot.view, slot.params) }
                .onFailure { Log.e(TAG, "Failed to resume capture overlay", it) }
        }
        if (ctrl.shouldShowRuntimeVisuals()) {
            renderer.resumeTriggerVisualWindows()
        }
        exclusionWindows.forEach { slot ->
            runCatching { addOverlayView(slot.view, slot.params) }
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
            removeOverlayView(slot.view)
        }
    }

    fun reattachCaptureWindows() {
        if (overlayLayoutSuspended()) return
        touchCaptureWindows.forEach { slot ->
            runCatching { addOverlayView(slot.view, slot.params) }
                .onFailure { Log.e(TAG, "Failed to reattach capture overlay", it) }
        }
        if (ctrl.shouldShowRuntimeVisuals()) {
            renderer.resumeTriggerVisualWindows()
        }
        exclusionWindows.forEach { slot ->
            runCatching { addOverlayView(slot.view, slot.params) }
                .onFailure { Log.e(TAG, "Failed to reattach exclusion overlay", it) }
        }
    }

    fun detachAllCaptureWindows() {
        detachTouchCaptureWindows()
        renderer.detachAllTriggerVisualWindows()
        detachAllExclusionWindows()
        purgeAllTrackedOverlayViews()
    }

    fun detachAllExclusionWindows() {
        detachExclusionViewsOnly()
        exclusionWindows.clear()
    }

    fun detachExclusionViewsOnly() {
        exclusionWindows.forEach { slot ->
            removeOverlayView(slot.view)
        }
    }

    fun attachCaptureWindows(presentation: EdgeGestureOverlayView) {
        if (overlayLayoutSuspended()) return
        val touchHandler: (android.view.MotionEvent) -> Boolean = { event ->
            if (ctrl.settings.triggerHandles(side).isEmpty()) {
                false
            } else {
                presentation.handleOverlayTouch(event)
            }
        }
        computeCaptureWindowBounds().forEachIndexed { index, bounds ->
            val params = createCaptureLayoutParams()
            applyCaptureLayout(params, bounds)
            val capture = EdgeTouchCaptureView(overlayContext, side, index, touchHandler)
            runCatching { addOverlayView(capture, params) }
                .onSuccess { touchCaptureWindows += CaptureWindow(capture, params) }
                .onFailure { Log.e(TAG, "Failed to add capture window", it) }
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
            runCatching { addOverlayView(exclusion, params) }
                .onSuccess { exclusionWindows += CaptureWindow(exclusion, params) }
                .onFailure { Log.e(TAG, "Failed to add exclusion window", it) }
        }
    }

    fun syncCaptureWindows(
        presentation: EdgeGestureOverlayView,
        forceLayout: Boolean = false,
        applyToWindowManager: Boolean = true,
    ) {
        if (!forceLayout && overlayLayoutSuspended()) return
        if (presentation.presentationShouldPassthroughTouches()) {
            if (applyToWindowManager) {
                detachTouchCaptureViewsOnly()
                detachExclusionViewsOnly()
            } else {
                touchCaptureWindows.clear()
                exclusionWindows.clear()
            }
            presentation.syncOverlayDialogZOrder()
            return
        }
        syncTouchCaptureWindows(presentation, applyToWindowManager)
        syncExclusionWindows(applyToWindowManager)
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

    private fun syncTouchCaptureWindows(
        presentation: EdgeGestureOverlayView,
        applyToWindowManager: Boolean = true,
    ) {
        val bounds = computeCaptureWindowBounds()
        val touchHandler: (android.view.MotionEvent) -> Boolean = { event ->
            if (ctrl.settings.triggerHandles(side).isEmpty()) {
                false
            } else {
                presentation.handleOverlayTouch(event)
            }
        }
        val passthrough = capturePassthroughSuspended || presentation.presentationShouldPassthroughTouches()
        while (touchCaptureWindows.size > bounds.size) {
            val slot = touchCaptureWindows.removeAt(touchCaptureWindows.lastIndex)
            removeOverlayView(slot.view)
        }
        bounds.forEachIndexed { index, bound ->
            if (index >= touchCaptureWindows.size) {
                if (!applyToWindowManager) return@forEachIndexed
                val params = createCaptureLayoutParams()
                applyCaptureLayout(params, bound)
                if (passthrough) {
                    applyPresentationPassthroughFlags(params)
                } else {
                    applyCaptureTouchFlags(params)
                }
                val capture = EdgeTouchCaptureView(overlayContext, side, index, touchHandler)
                runCatching { addOverlayView(capture, params) }
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
                if (applyToWindowManager) {
                    runCatching { windowManager.updateViewLayout(slot.view, slot.params) }
                        .onFailure { Log.e(TAG, "Failed to sync capture window layout", it) }
                }
            }
        }
    }

    private fun syncExclusionWindows(applyToWindowManager: Boolean = true) {
        val bounds = computeSystemGestureExclusionBounds()
        while (exclusionWindows.size > bounds.size) {
            val slot = exclusionWindows.removeAt(exclusionWindows.lastIndex)
            removeOverlayView(slot.view)
        }
        bounds.forEachIndexed { index, bound ->
            if (index >= exclusionWindows.size) {
                if (!applyToWindowManager) return@forEachIndexed
                val params = createCaptureLayoutParams()
                applyCaptureLayout(params, bound)
                OverlayWindowTypes.applyExclusionPassthroughFlags(params)
                val exclusion = EdgeSystemGestureExclusionView(overlayContext)
                runCatching { addOverlayView(exclusion, params) }
                    .onSuccess { exclusionWindows += CaptureWindow(exclusion, params) }
                    .onFailure { Log.e(TAG, "Failed to add exclusion window", it) }
            } else {
                val slot = exclusionWindows[index]
                applyCaptureLayout(slot.params, bound)
                OverlayWindowTypes.applyExclusionPassthroughFlags(slot.params)
                if (applyToWindowManager) {
                    runCatching { windowManager.updateViewLayout(slot.view, slot.params) }
                        .onFailure { Log.e(TAG, "Failed to sync exclusion window layout", it) }
                }
            }
        }
    }

    private fun applyOverlayWindowBrightness(fraction: Float?) {
        if (fraction == null) {
            clearOverlayWindowBrightness()
        }
    }

    private fun flushOverlayWindowBrightness() = Unit

    private fun clearPresentationBrightnessOverride(params: WindowManager.LayoutParams) {
        OverlayWindowTypes.ensureNoBrightnessOverride(params)
    }

    private fun computeCaptureWindowBounds(): List<CollapsedWindowBounds> =
        GestureZoneLayout.computeTouchCaptureWindowBounds(
            settings = ctrl.settings,
            side = side,
            screenWidthPx = ctrl.screenWidthPx,
            screenHeightPx = ctrl.screenHeightPx,
            density = ctrl.density,
        )

    private fun computeSystemGestureExclusionBounds(): List<CollapsedWindowBounds> =
        GestureZoneLayout.computeSystemGestureExclusionBounds(
            settings = ctrl.settings,
            side = side,
            screenWidthPx = ctrl.screenWidthPx,
            screenHeightPx = ctrl.screenHeightPx,
            density = ctrl.density,
        )

    private fun applyPresentationTouchFlags(
        view: EdgeGestureOverlayView,
        params: WindowManager.LayoutParams,
    ) {
        if (view.needsPresentationDirectTouch()) {
            val panelOpen = view.panelMode() != OverlayPanelMode.NONE
            if (!panelOpen && view.presentationShouldPassthroughTouches()) {
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
        OverlayWindowTypes.ensureNoBrightnessOverride(params)
    }

    private fun applyCaptureLayout(
        params: WindowManager.LayoutParams,
        bounds: CollapsedWindowBounds,
    ) {
        params.width = bounds.widthPx
        params.height = bounds.heightPx
        params.x = bounds.xPx
        params.y = bounds.yPx
        params.gravity = windowGravity()
        if (side == PanelSide.BOTTOM || side == PanelSide.TOP) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun windowGravity(): Int = when (side) {
        PanelSide.LEFT -> Gravity.TOP or Gravity.START
        PanelSide.RIGHT -> Gravity.TOP or Gravity.END
        PanelSide.BOTTOM, PanelSide.TOP -> Gravity.TOP or Gravity.START
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

    fun markChromeBelowPanel() {
        chromeZOrderFront = false
        renderer.markChromeBelowPanel()
    }

    fun bringEdgeWindowsToFront(forceReAdd: Boolean = !chromeZOrderFront) {
        if (edgeOverlayDetached || overlayLayoutSuspended()) return
        if (OverlaySceneController.isEdgeGestureActive()) return
        touchCaptureWindows.forEach { slot ->
            bringWindowToFront(slot.view, slot.params, forceReAdd)
        }
        if (ctrl.shouldShowRuntimeVisuals()) {
            renderer.bringTriggerVisualWindowsToFront(forceReAdd)
        }
        exclusionWindows.forEach { slot ->
            bringWindowToFront(slot.view, slot.params, forceReAdd)
        }
        chromeZOrderFront = true
    }

    private var chromeZOrderFront = true

    private fun bringWindowToFront(
        view: View,
        params: WindowManager.LayoutParams,
        forceReAdd: Boolean,
    ) {
        if (!view.isAttachedToWindow) return
        OverlayWindowTypes.ensureNoBrightnessOverride(params)
        runCatching { windowManager.updateViewLayout(view, params) }
            .onFailure { Log.e(TAG, "Failed to update edge window layout", it) }
    }

    internal fun overlayLayoutSuspended(): Boolean =
        edgeOverlayDetached || OverlayTrampolineGuard.blocksOverlayPresentationTouch()

    internal data class CaptureWindow(
        val view: View,
        var params: WindowManager.LayoutParams,
    )

    companion object {
        private const val TAG = "SideOverlayController"
    }
}
