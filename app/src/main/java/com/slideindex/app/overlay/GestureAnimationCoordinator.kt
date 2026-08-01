package com.slideindex.app.overlay

import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.overlay.animation.GestureAnimationOverlayRegistry
import com.slideindex.app.overlay.animation.GestureAnimationState
import com.slideindex.app.settings.AppSettings
import kotlin.math.abs
import kotlin.math.hypot

class GestureAnimationCoordinator(
    private val side: PanelSide,
    private val gestureSessionProvider: () -> GestureSession,
    private val pathRecognizerProvider: () -> SwipePathRecognizer,
    private val settingsProvider: () -> AppSettings,
    private val post: (() -> Unit) -> Unit,
) {
    private val overlay get() = GestureAnimationOverlayRegistry.controller(side)

    private var pendingDownRawX = 0f
    private var pendingDownRawY = 0f
    private var animationStarted = false

    fun applySettings(settings: AppSettings) {
        overlay.applySettings(settings)
    }

    fun hide() {
        animationStarted = false
        overlay.hide()
    }

    fun onTouchDown(rawX: Float, rawY: Float) {
        if (!settingsProvider().gestureHintEnabled) return
        pendingDownRawX = rawX
        pendingDownRawY = rawY
        animationStarted = false
        overlay.applySettings(settingsProvider(), gestureSessionProvider().activeHandleId())
    }

    fun onTouchMove(rawX: Float, rawY: Float) {
        if (!settingsProvider().gestureHintEnabled) return
        if (!animationStarted && shouldShowGestureAnimation(rawX, rawY)) {
            startAnimationIfReady()
        }
        val state = overlay.animationState
        if (state == null) {
            if (overlay.isAttached && shouldShowGestureAnimation(rawX, rawY)) {
                post { onTouchMove(rawX, rawY) }
            }
            return
        }
        handleDrag(state, rawX, rawY)
    }

    fun onTouchUp(rawX: Float, rawY: Float) {
        if (!animationStarted) {
            overlay.hide()
            return
        }
        if (!shouldShowGestureAnimation(rawX, rawY)) {
            animationStarted = false
            overlay.hide()
            return
        }
        overlay.animationState?.let { finishIfNeeded(it) }
        animationStarted = false
    }

    fun onTouchCanceled() {
        animationStarted = false
        overlay.hide()
    }

    private fun startAnimationIfReady() {
        val state = overlay.animationState
        if (state == null) {
            if (overlay.isAttached) {
                post { startAnimationIfReady() }
            }
            return
        }
        if (animationStarted) return
        animationStarted = true
        state.onDragStart(pendingDownRawX, pendingDownRawY)
    }

    private fun shouldShowGestureAnimation(rawX: Float, rawY: Float): Boolean {
        val recognizer = pathRecognizerProvider()
        if (recognizer.currentInwardPx() > MIN_GESTURE_ANIMATION_PX) return true
        if (abs(recognizer.currentEdgeOffsetPx()) > MIN_GESTURE_ANIMATION_PX) return true
        if (recognizer.currentSwipeDirection() != null &&
            recognizer.currentSwipeDistancePx() > MIN_GESTURE_ANIMATION_PX
        ) {
            return true
        }
        return hypot(
            (rawX - pendingDownRawX).toDouble(),
            (rawY - pendingDownRawY).toDouble(),
        ) > MIN_GESTURE_ANIMATION_PX
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
        animationStarted = false
    }

    fun dismissForFloatingPointerHandoff() = dismissForContinuedOverlayHandoff()

    fun dismissForContinuedOverlayHandoff() {
        if (!FloatingPointerOverlayWindow.isConsumingEdgeGestureTouch() &&
            !RegionalPickOverlay.isConsumingEdgeGestureTouch()
        ) {
            return
        }
        overlay.animationState?.let { finishIfNeeded(it) }
        animationStarted = false
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
    }

    private companion object {
        /** 低于此位移视为单击，不展示手势提示动画。 */
        const val MIN_GESTURE_ANIMATION_PX = 4f
    }
}
