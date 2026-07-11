package com.slideindex.app.overlay

import android.util.Log
import android.view.WindowManager
import com.slideindex.app.gesture.CollapsedWindowBounds
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.settings.triggerHandles

internal class SideOverlayRenderer(
    private val ctrl: SideOverlayController,
) {
    private val windowManager get() = ctrl.windowManager
    private val androidWindowManager get() = ctrl.androidWindowManager
    private val overlayContext get() = ctrl.overlayContext
    private val side get() = ctrl.side

    internal val triggerVisualWindows = mutableListOf<SideOverlayWindowManager.CaptureWindow>()

    fun syncTriggerVisualWindows() {
        if (windowManager.edgeOverlayDetached || windowManager.presentationView == null) return
        val handles = ctrl.settings.triggerHandles(side)
        val bounds = computeTriggerVisualBounds()
        while (triggerVisualWindows.size > bounds.size) {
            val slot = triggerVisualWindows.removeAt(triggerVisualWindows.lastIndex)
            runCatching { androidWindowManager.removeView(slot.view) }
                .onFailure { Log.e(TAG, "Failed to remove trigger visual window", it) }
        }
        bounds.forEachIndexed { index, bound ->
            val design = handles.getOrNull(index)?.design ?: return@forEachIndexed
            if (index >= triggerVisualWindows.size) {
                val params = createTriggerVisualLayoutParams()
                applyCaptureLayout(params, bound)
                val visual = TriggerVisualOverlayView(overlayContext)
                visual.applyVisual(side, design, index)
                runCatching { androidWindowManager.addView(visual, params) }
                    .onSuccess { triggerVisualWindows += SideOverlayWindowManager.CaptureWindow(visual, params) }
                    .onFailure { Log.e(TAG, "Failed to add trigger visual window", it) }
            } else {
                val slot = triggerVisualWindows[index]
                applyCaptureLayout(slot.params, bound)
                (slot.view as? TriggerVisualOverlayView)?.applyVisual(side, design, index)
                runCatching { androidWindowManager.updateViewLayout(slot.view, slot.params) }
                    .onFailure { Log.e(TAG, "Failed to sync trigger visual window layout", it) }
            }
        }
    }

    fun attachTriggerVisualWindows() {
        val handles = ctrl.settings.triggerHandles(side)
        computeTriggerVisualBounds().forEachIndexed { index, bounds ->
            val design = handles.getOrNull(index)?.design ?: return@forEachIndexed
            val params = createTriggerVisualLayoutParams()
            applyCaptureLayout(params, bounds)
            val visual = TriggerVisualOverlayView(overlayContext).apply {
                applyVisual(side, design, index)
            }
            androidWindowManager.addView(visual, params)
            triggerVisualWindows += SideOverlayWindowManager.CaptureWindow(visual, params)
        }
    }

    fun detachAllTriggerVisualWindows() {
        triggerVisualWindows.forEach { slot ->
            runCatching { androidWindowManager.removeView(slot.view) }
        }
        triggerVisualWindows.clear()
    }

    fun resumeTriggerVisualWindows() {
        triggerVisualWindows.forEach { slot ->
            runCatching { androidWindowManager.addView(slot.view, slot.params) }
                .onFailure { Log.e(TAG, "Failed to resume trigger visual overlay", it) }
        }
    }

    fun applyPreviewPresentationWindow() {
        if (!windowManager.presentationAttached) return
        val content = windowManager.presentationView ?: return
        val root = windowManager.presentationRoot() ?: return
        val params = windowManager.presentationParams ?: return
        content.applyExpandedOverlayLayout()
        OverlayWindowTypes.applyFullScreen(params)
        applyPreviewPresentationFlags(params)
        runCatching { androidWindowManager.updateViewLayout(root, params) }
    }

    private fun computeTriggerVisualBounds(): List<CollapsedWindowBounds> =
        GestureZoneLayout.computeCaptureWindowBounds(
            settings = ctrl.settings,
            side = side,
            screenHeightPx = ctrl.screenHeightPx,
            density = ctrl.density,
        )

    private fun createTriggerVisualLayoutParams(): WindowManager.LayoutParams =
        OverlayWindowTypes.createCaptureParams(overlayContext).also {
            OverlayWindowTypes.applyExclusionPassthroughFlags(it)
        }

    private fun applyCaptureLayout(
        params: WindowManager.LayoutParams,
        bounds: CollapsedWindowBounds,
    ) {
        params.width = bounds.widthPx
        params.height = bounds.heightPx
        params.x = 0
        params.y = bounds.yPx
        params.gravity = when (side) {
            PanelSide.LEFT -> android.view.Gravity.TOP or android.view.Gravity.START
            PanelSide.RIGHT -> android.view.Gravity.TOP or android.view.Gravity.END
        }
    }

    private fun applyPreviewPresentationFlags(params: WindowManager.LayoutParams) {
        OverlayWindowTypes.applyPreviewPresentationFlags(params)
    }

    companion object {
        private const val TAG = "SideOverlayController"
    }
}
