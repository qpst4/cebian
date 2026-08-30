package com.slideindex.app.overlay



import com.slideindex.app.gesture.GestureAction

import com.slideindex.app.gesture.GestureSession

import com.slideindex.app.gesture.GestureTriggerType

import com.slideindex.app.gesture.SwipePathRecognizer

import com.slideindex.app.overlay.animation.GestureAnimationOverlayRegistry
import com.slideindex.app.overlay.compositor.OverlaySceneController

import com.slideindex.app.overlay.animation.GestureAnimationState

import com.slideindex.app.settings.AppSettings

import com.slideindex.app.settings.actionFor

import kotlin.math.abs



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

        OverlaySceneController.onEdgeGestureStarted()

        pendingDownRawX = rawX

        pendingDownRawY = rawY

        animationStarted = false

        overlay.applySettings(settingsProvider(), gestureSessionProvider().activeHandleId())

        overlay.bringToFront()

    }



    fun onTouchMove(rawX: Float, rawY: Float) {

        if (!settingsProvider().gestureHintEnabled) return

        if (!animationStarted && shouldShowGestureAnimation()) {

            startAnimationIfReady()

        }

        val state = overlay.animationState

        if (state == null) {

            if (overlay.isAttached && shouldShowGestureAnimation()) {

                post { onTouchMove(rawX, rawY) }

            }

            return

        }

        handleDrag(state, rawX, rawY)

    }



    fun onTouchUp(rawX: Float, rawY: Float) {

        val recognizer = pathRecognizerProvider()

        val stillTap = recognizer.movementPxFromStart() <

            recognizer.tapDisqualifyMovementPx(classifyOptions())

        if (!animationStarted || stillTap || !shouldShowGestureAnimation()) {

            animationStarted = false

            overlay.hide()

            OverlaySceneController.onEdgeGestureEnded()

            return

        }

        overlay.animationState?.let { finishIfNeeded(it) }

        animationStarted = false

        OverlaySceneController.onEdgeGestureEnded()

    }



    fun onTouchCanceled() {

        animationStarted = false

        overlay.hide()

        OverlaySceneController.onEdgeGestureEnded()

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
        overlay.bringToFront()

        state.onDragStart(pendingDownRawX, pendingDownRawY)

    }



    /**

     * 用内滑 / 沿边分量判断真实滑动，避免顶部单击时斜向抖动（总位移略大但分量很小）

     * 被误判为 DOWN_RIGHT 并闪出右下动画。

     */

    private fun shouldShowGestureAnimation(): Boolean {

        val recognizer = pathRecognizerProvider()

        val threshold = animationStartThresholdPx()

        if (recognizer.movementPxFromStart() < threshold) return false

        if (recognizer.currentInwardPx() >= threshold) return true

        if (abs(recognizer.currentEdgeOffsetPx()) >= threshold) return true

        return recognizer.currentSwipeDistancePx() >= threshold

    }



    private fun animationStartThresholdPx(): Float =
        pathRecognizerProvider().gestureHintStartThresholdPx()



    private fun classifyOptions(): SwipePathRecognizer.ClassifyOptions {
        val settings = settingsProvider()
        val handleId = gestureSessionProvider().activeHandleId()
        val preferLenientTap = settings.actionFor(
            side,
            GestureTriggerType.SHORT_SINGLE_TAP,
            handleId,
        ) is GestureAction.ClickPassthrough
        val base = if (preferLenientTap) {
            SwipePathRecognizer.ClassifyOptions.LENIENT_SINGLE_TAP
        } else {
            SwipePathRecognizer.ClassifyOptions.DEFAULT
        }
        return base.copy(
            isTriggerConfigured = { trigger ->
                settings.actionFor(side, trigger, handleId) !is GestureAction.None
            },
        )
    }



    private fun handleDrag(state: GestureAnimationState, rawX: Float, rawY: Float) {

        if (!settingsProvider().gestureHintEnabled) return

        if (shouldDismissDuringSession()) {
            if (state.isActive) {
                overlay.hide()
            }
            return
        }

        if (!state.isActive) return

        val recognizer = pathRecognizerProvider()
        val classification = recognizer.classifyPartial(rawX, rawY, classifyOptions())
        val direction = if (shouldShowGestureAnimation()) {
            recognizer.currentSwipeDirection()
        } else {
            null
        }

        state.onDrag(
            rawX = rawX,
            rawY = rawY,
            swipeDirection = direction,
            inwardPx = recognizer.currentInwardPx(),
            currentTrigger = classification?.trigger,
            currentDistancePx = recognizer.currentSwipeDistancePx(),
        )

    }



    fun onSessionStartDismissIfNeeded() {

        overlay.animationState?.let { finishIfNeeded(it) }

        animationStarted = false

    }



    fun dismissForFloatingPointerHandoff() = dismissForContinuedOverlayHandoff()



    fun dismissForContinuedOverlayHandoff() {
        if (!EdgeContinuedOverlayHandoff.shouldDismissGestureHint()) return
        EdgeContinuedOverlayHandoff.markGestureHintDismissed()
        overlay.hide()
        animationStarted = false
    }



    private fun shouldDismissDuringSession(): Boolean {

        val gestureSession = gestureSessionProvider()

        if (!gestureSession.isActive()) return false

        return gestureSession.isMoveTimeActionLocked() ||

            gestureSession.isAdjustMode() ||

            gestureSession.isContinuousPickActive() ||

            gestureSession.panelMode() != OverlayPanelMode.NONE

    }



    private fun finishIfNeeded(state: GestureAnimationState) {

        if (!settingsProvider().gestureHintEnabled) return

        state.onDragEnd()

    }

}


