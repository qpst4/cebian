package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Joystick-sized touch target. Touches elsewhere on the screen pass through to apps below.
 * [FLAG_WATCH_OUTSIDE_TOUCH] delivers ACTION_OUTSIDE for optional click-to-hide. Echoes from
 * injected pointer taps carry zeroed coordinates on modern Android, so
 * [FloatingPointerOverlayWindow.shouldDismissOnOutsideTouch] also suppresses dismiss for a
 * short window after each injected tap.
 */
@SuppressLint("ViewConstructor") // Programmatically created floating pointer host
internal class FloatingPointerHostLayout(
    context: Context,
    session: FloatingPointerSession,
    settingsProvider: () -> com.slideindex.app.settings.AppSettings,
    private val fingerTrackingMove: (fingerRawX: Float, fingerRawY: Float, fingerLocalX: Float, fingerLocalY: Float) -> Unit,
    private val pointerPositionChanged: (pointerX: Float, pointerY: Float) -> Unit,
    private val gestureEnd: (centerX: Float, centerY: Float, isTap: Boolean) -> Unit,
    private val pointerClick: (rawX: Float, rawY: Float) -> Unit,
    private val pointerClickAndDismiss: (rawX: Float, rawY: Float) -> Unit,
    private val outsideDismissPrepare: () -> Unit,
    private val quickSwipeDismiss: () -> Unit,
    private val dismiss: () -> Unit,
    private val radialMenuOpened: () -> Unit,
    private val radialMenuClosed: () -> Unit,
    private val radialMenuAction: (slotIndex: Int, fingerStillDown: Boolean) -> Unit,
    private val onExpandTouchCapture: () -> Unit,
    private val joystickLongPressAction: (action: com.slideindex.app.gesture.GestureAction) -> Unit,
    private val startPendingGestureCapture: (action: com.slideindex.app.gesture.GestureAction) -> Unit,
    private val activity: () -> Unit,
    private val haptic: () -> Unit,
    private val dismissOnOutsideTouch: (MotionEvent) -> Boolean,
    private val touchCycleComplete: () -> Unit,
    private val swallowInjectEcho: (MotionEvent) -> Boolean,
    private val onEnsureTouchOverlayInteractive: () -> Unit,
    private val resolveFingerLocalInTouchOverlay: (Float, Float) -> Pair<Float, Float>,
    private val onFinishEdgeHandoffTouchCapture: (Float, Float) -> Unit,
    private val pointerTapInjectionActive: () -> Boolean,
) : FrameLayout(context), FloatingPointerInputHandler.Host {
    private val inputHandler = FloatingPointerInputHandler(
        session = session,
        settingsProvider = settingsProvider,
        host = this,
    )

    init {
        isHapticFeedbackEnabled = false
    }

    override fun captureAllPointers() {
        runCatching { requestPointerCapture() }
    }

    override fun releaseAllPointers() {
        runCatching { releasePointerCapture() }
    }

    override fun onFingerTrackingMove(
        fingerRawX: Float,
        fingerRawY: Float,
        fingerLocalX: Float,
        fingerLocalY: Float,
    ) = fingerTrackingMove(fingerRawX, fingerRawY, fingerLocalX, fingerLocalY)

    override fun onPointerPositionChanged(pointerX: Float, pointerY: Float) =
        pointerPositionChanged(pointerX, pointerY)

    override fun onGestureEnd(centerX: Float, centerY: Float, isTap: Boolean) =
        gestureEnd(centerX, centerY, isTap)

    override fun onPointerClick(rawX: Float, rawY: Float) = pointerClick(rawX, rawY)

    override fun onPointerClickAndDismiss(rawX: Float, rawY: Float) =
        pointerClickAndDismiss(rawX, rawY)

    override fun onOutsideDismissPrepare() = outsideDismissPrepare()

    override fun onQuickSwipeDismiss() = quickSwipeDismiss()

    override fun onDismiss() = dismiss()

    override fun hostContext(): Context = context.applicationContext

    override fun onRadialMenuOpened() = radialMenuOpened()

    override fun onRadialMenuClosed() = radialMenuClosed()

    override fun onRadialMenuAction(slotIndex: Int, fingerStillDown: Boolean) =
        radialMenuAction(slotIndex, fingerStillDown)

    override fun expandTouchCapture() = onExpandTouchCapture()

    override fun onJoystickLongPressAction(action: com.slideindex.app.gesture.GestureAction) =
        joystickLongPressAction(action)

    override fun onStartPendingGestureCapture(action: com.slideindex.app.gesture.GestureAction) =
        startPendingGestureCapture(action)

    override fun onActivity() = activity()

    override fun onHaptic() = haptic()

    override fun shouldDismissOnOutsideTouch(event: MotionEvent): Boolean =
        dismissOnOutsideTouch(event)

    override fun onTouchCycleComplete() = touchCycleComplete()

    override fun shouldSwallowInjectEcho(event: MotionEvent): Boolean = swallowInjectEcho(event)

    override fun isPointerTapInjectionActive(): Boolean = pointerTapInjectionActive()

    override fun ensureTouchOverlayInteractive() = onEnsureTouchOverlayInteractive()

    override fun fingerLocalInTouchOverlay(fingerRawX: Float, fingerRawY: Float): Pair<Float, Float> =
        resolveFingerLocalInTouchOverlay(fingerRawX, fingerRawY)

    override fun finishEdgeHandoffTouchCapture(fingerRawX: Float, fingerRawY: Float) =
        onFinishEdgeHandoffTouchCapture(fingerRawX, fingerRawY)

    fun beginContinuedGesture(rawX: Float, rawY: Float, downTimeMs: Long) =
        inputHandler.beginContinuedGesture(rawX, rawY, downTimeMs)

    fun forwardContinuedTouch(event: MotionEvent): Boolean =
        inputHandler.forwardContinuedTouch(event)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        inputHandler.dispatchTouchEvent(event)
}
