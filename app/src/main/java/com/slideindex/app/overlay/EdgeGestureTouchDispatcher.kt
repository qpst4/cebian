package com.slideindex.app.overlay

import android.view.MotionEvent
import com.slideindex.app.gesture.GestureSession

internal class EdgeGestureTouchDispatcher(
    private val gestureSession: GestureSession,
    private val adjustPanelController: AdjustPanelOverlayController,
    private val quickLauncherController: QuickLauncherOverlayController,
    private val shellCoordinator: ShellPanelOverlayController,
    private val taskSwitcherController: TaskSwitcherOverlayController,
    private val indexPanelRenderer: IndexPanelRenderer,
    private val gestureAnimationCoordinator: GestureAnimationCoordinator,
    private val rawToLocal: (Float, Float) -> Pair<Float, Float>,
    private val forEachGesturePoint: (
        MotionEvent,
        Float,
        Float,
        Boolean,
        (Float, Float, Float, Float) -> Unit,
    ) -> Unit,
    private val onGestureTrackingStart: () -> Unit,
    private val onSyncZoneLayout: () -> Unit,
    private val onForceRecoverInteractionState: () -> Unit,
    private val edgeCaptureTouchActive: () -> Boolean,
    private val setEdgeCaptureTouchActive: (Boolean) -> Unit,
    private val composeOverlayDialogShowing: () -> Boolean,
) {
    fun handleTouch(event: MotionEvent): Boolean {
        if (composeOverlayDialogShowing()) return false
        val (localX, localY) = rawToLocal(event.rawX, event.rawY)
        if (adjustPanelController.hasAdjustPanel() && !gestureSession.isActive()) {
            if (adjustPanelController.handleTouch(event, localX, localY)) return true
        }
        when (gestureSession.panelMode()) {
            OverlayPanelMode.QUICK_LAUNCHER ->
                return quickLauncherController.handleTouch(event, localX, localY)
            OverlayPanelMode.SHELL_COMMANDS ->
                return shellCoordinator.handleTouch(event, localX, localY)
            OverlayPanelMode.TASK_SWITCHER ->
                return taskSwitcherController.handleTouch(event, localX, localY)
            OverlayPanelMode.INDEX -> return indexPanelRenderer.handleTouch(event, localX, localY)
            OverlayPanelMode.NONE -> Unit
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when {
                    adjustPanelController.hasAdjustPanel() &&
                        !gestureSession.isActive() &&
                        !adjustPanelController.isDismissing() ->
                        onForceRecoverInteractionState()
                    gestureSession.panelMode() != OverlayPanelMode.NONE && !gestureSession.isActive() ->
                        gestureSession.forceReset(notifySessionEnd = true)
                    gestureSession.isActive() -> {
                        shellCoordinator.closePanelTrampolineIfContinuous()
                        gestureSession.forceReset(notifySessionEnd = false)
                    }
                }
                if (gestureSession.onTouchDown(event.rawX, event.rawY, localX, localY)) {
                    FloatingPointerAreaPreviewOverlay.onEdgeTriggerTouch(event.rawX, event.rawY)
                    setEdgeCaptureTouchActive(true)
                    onSyncZoneLayout()
                    onGestureTrackingStart()
                    gestureAnimationCoordinator.onTouchDown(event.rawX, event.rawY)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!edgeCaptureTouchActive()) return false
                if (!gestureSession.isActive()) {
                    gestureAnimationCoordinator.dismissForFloatingPointerHandoff()
                    FloatingPointerOverlayWindow.forwardContinuedTouch(event)
                    return true
                }
                forEachGesturePoint(event, localX, localY, true) { rawX, rawY, lx, ly ->
                    gestureSession.onTouchMove(rawX, rawY, lx, ly)
                    if (FloatingPointerOverlayWindow.isConsumingEdgeGestureTouch()) {
                        gestureAnimationCoordinator.dismissForFloatingPointerHandoff()
                    } else {
                        gestureAnimationCoordinator.onTouchMove(rawX, rawY)
                    }
                }
                if (FloatingPointerOverlayWindow.isConsumingEdgeGestureTouch()) {
                    gestureAnimationCoordinator.dismissForFloatingPointerHandoff()
                    FloatingPointerOverlayWindow.forwardContinuedTouch(event)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!gestureSession.isActive()) {
                    val consumed = edgeCaptureTouchActive()
                    val handoff = FloatingPointerOverlayWindow.forwardContinuedTouch(event)
                    if (consumed) {
                        setEdgeCaptureTouchActive(false)
                        gestureAnimationCoordinator.onTouchUp()
                    }
                    return consumed || handoff || event.actionMasked == MotionEvent.ACTION_CANCEL
                }
                setEdgeCaptureTouchActive(false)
                val canceled = event.actionMasked == MotionEvent.ACTION_CANCEL
                forEachGesturePoint(event, localX, localY, true) { rawX, rawY, lx, ly ->
                    gestureSession.onTouchMove(rawX, rawY, lx, ly)
                    gestureAnimationCoordinator.onTouchMove(rawX, rawY)
                }
                gestureAnimationCoordinator.onTouchUp()
                gestureSession.onTouchUp(event.rawX, event.rawY, localX, localY)
                FloatingPointerOverlayWindow.forwardContinuedTouch(event)
                if (canceled) {
                    gestureAnimationCoordinator.onTouchCanceled()
                }
                return true
            }
        }
        return false
    }
}
