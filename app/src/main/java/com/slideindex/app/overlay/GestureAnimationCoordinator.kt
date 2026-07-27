package com.slideindex.app.overlay

import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.overlay.animation.GestureAnimationOverlayRegistry
import com.slideindex.app.overlay.animation.GestureAnimationState
import com.slideindex.app.settings.AppSettings

class GestureAnimationCoordinator(
    private val side: PanelSide,
    private val gestureSessionProvider: () -> GestureSession,
    private val pathRecognizerProvider: () -> SwipePathRecognizer,
    private val settingsProvider: () -> AppSettings,
    private val post: (() -> Unit) -> Unit,
) {
    private val overlay get() = GestureAnimationOverlayRegistry.controller(side)

    fun applySettings(settings: AppSettings) {
        overlay.applySettings(settings)
    }

    fun hide() {
        overlay.hide()
    }

    fun onTouchDown(rawX: Float, rawY: Float) {
        if (!settingsProvider().gestureHintEnabled) return
        overlay.applySettings(settingsProvider(), gestureSessionProvider().activeHandleId())
        val state = overlay.animationState
        if (state == null) {
            if (overlay.isAttached) {
                post { onTouchDown(rawX, rawY) }
            }
            return
        }
        state.onDragStart(rawX, rawY)
    }

    fun onTouchMove(rawX: Float, rawY: Float) {
        val state = overlay.animationState ?: return
        handleDrag(state, rawX, rawY)
    }

    fun onTouchUp() {
        overlay.animationState?.let { finishIfNeeded(it) }
    }

    fun onTouchCanceled() {
        overlay.hide()
    }

    private fun handleDrag(state: GestureAnimationState, rawX: Float, rawY: Float) {
        if (!settingsProvider().gestureHintEnabled) return
        if (shouldDismissDuringSession()) {
            if (state.isActive) {
                finishIfNeeded(state)
            }
            return
        }
        if (!state.isActive) return
        state.onDrag(
            rawX = rawX,
            rawY = rawY,
            swipeDirection = pathRecognizerProvider().currentSwipeDirection(),
            inwardPx = pathRecognizerProvider().currentInwardPx(),
        )
    }

    fun onSessionStartDismissIfNeeded() {
        overlay.animationState?.let { finishIfNeeded(it) }
    }

    fun dismissForFloatingPointerHandoff() {
        if (!FloatingPointerOverlayWindow.isConsumingEdgeGestureTouch()) return
        overlay.animationState?.let { finishIfNeeded(it) }
    }

    private fun shouldDismissDuringSession(): Boolean {
        val gestureSession = gestureSessionProvider()
        if (!gestureSession.isActive()) return false
        return gestureSession.isMoveTimeActionLocked() ||
            gestureSession.isAdjustMode() ||
            gestureSession.panelMode() != OverlayPanelMode.NONE
    }

    private fun finishIfNeeded(state: GestureAnimationState) {
        if (!settingsProvider().gestureHintEnabled) return
        state.onDragEnd()
        overlay.hideAfterGesture()
    }
}
