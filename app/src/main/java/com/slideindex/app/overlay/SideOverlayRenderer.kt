package com.slideindex.app.overlay

import android.util.Log
import android.view.WindowManager
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.settings.triggerHandles

/**
 * Edge chrome helpers. Touch + visual share [EdgeTouchCaptureView] windows
 * (SideGesture-style one window per handle); this type only syncs draw state.
 */
internal class SideOverlayRenderer(
    private val ctrl: SideOverlayController,
) {
    private val windowManager get() = ctrl.windowManager
    private val androidWindowManager get() = ctrl.androidWindowManager
    private val side get() = ctrl.side

    /** Kept empty for API compatibility with older call sites that checked size. */
    internal val triggerVisualWindows = mutableListOf<SideOverlayWindowManager.CaptureWindow>()

    fun syncTriggerVisualWindows() {
        val show = ctrl.shouldShowRuntimeVisuals()
        val handles = ctrl.settings.triggerHandles(side)
        windowManager.touchCaptureWindows.forEachIndexed { index, slot ->
            val capture = slot.view as? EdgeTouchCaptureView ?: return@forEachIndexed
            val design = handles.getOrNull(index)?.design ?: TriggerHandleDesign()
            capture.applyVisual(design, show)
            capture.setExcludeSystemGestures(shouldExcludeSystemGestures())
        }
    }

    fun attachTriggerVisualWindows() {
        // Visuals live on capture windows; nothing to add.
        syncTriggerVisualWindows()
    }

    fun detachAllTriggerVisualWindows() {
        windowManager.touchCaptureWindows.forEach { slot ->
            (slot.view as? EdgeTouchCaptureView)?.applyVisual(TriggerHandleDesign(), visible = false)
        }
        triggerVisualWindows.clear()
    }

    fun detachTriggerVisualViewsOnly() {
        detachAllTriggerVisualWindows()
    }

    fun resumeTriggerVisualWindows() {
        syncTriggerVisualWindows()
    }

    fun markChromeBelowPanel() {
        chromeZOrderFront = false
    }

    fun bringTriggerVisualWindowsToFront(forceReAdd: Boolean = !chromeZOrderFront) {
        // Capture windows already reordered by [SideOverlayWindowManager.bringEdgeWindowsToFront].
        chromeZOrderFront = true
    }

    private var chromeZOrderFront = true

    fun applyPreviewPresentationWindow() {
        if (!windowManager.presentationAttached) return
        val content = windowManager.presentationView ?: return
        val root = windowManager.presentationRoot() ?: return
        val params = windowManager.presentationParams ?: return
        content.applyExpandedOverlayLayout()
        OverlayWindowTypes.applyFullScreen(params)
        OverlayWindowTypes.ensureNoBrightnessOverride(params)
        OverlayWindowTypes.applyPreviewPresentationFlags(params)
        runCatching { androidWindowManager.updateViewLayout(root, params) }
            .onFailure { Log.e(TAG, "Failed to apply preview presentation window", it) }
    }

    private fun shouldExcludeSystemGestures(): Boolean =
        !side.isVerticalEdge && ctrl.settings.interceptSystemBackGesture

    companion object {
        private const val TAG = "SideOverlayController"
    }
}
