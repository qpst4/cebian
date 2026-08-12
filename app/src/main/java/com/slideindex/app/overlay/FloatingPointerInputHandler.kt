package com.slideindex.app.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.settings.AppSettings
import kotlin.math.hypot
import kotlin.math.max

internal class FloatingPointerInputHandler(
    private val session: FloatingPointerSession,
    private val settingsProvider: () -> AppSettings,
    private val host: Host,
) {
    interface Host {
        fun captureAllPointers()
        fun releaseAllPointers()
        fun onFingerTrackingMove(
            fingerRawX: Float,
            fingerRawY: Float,
            fingerLocalX: Float,
            fingerLocalY: Float,
        )
        fun onPointerPositionChanged(pointerX: Float, pointerY: Float)
        fun onGestureEnd(centerX: Float, centerY: Float, isTap: Boolean)
        fun onPointerClick(rawX: Float, rawY: Float)
        fun onPointerClickAndDismiss(rawX: Float, rawY: Float)
        fun onOutsideDismissPrepare()
        fun onQuickSwipeDismiss()
        fun onDismiss()
        fun onRadialMenuOpened()
        fun onRadialMenuClosed()
        fun onRadialMenuAction(slotIndex: Int, fingerStillDown: Boolean)
        fun expandTouchCapture()
        fun onJoystickLongPressAction(action: GestureAction)
        fun onStartPendingGestureCapture(action: GestureAction)
        fun onActivity()
        fun onHaptic()
        fun shouldDismissOnOutsideTouch(event: MotionEvent): Boolean
        fun onTouchCycleComplete()
        fun hostContext(): Context
        fun shouldSwallowInjectEcho(event: MotionEvent): Boolean
        fun fingerLocalInTouchOverlay(fingerRawX: Float, fingerRawY: Float): Pair<Float, Float>
        fun ensureTouchOverlayInteractive()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hoverSelectController = FloatingPointerHoverSelectController(session, mainHandler)
    internal var capturing = false
    /** True while an edge continuous handoff finger is still driving this session. */
    private var fromContinuedEdge = false
    private var movedBeyondTap = false
    private var longPressTriggered = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var restJoystickX = 0f
    private var restJoystickY = 0f
    private var downLocalX = 0f
    private var downLocalY = 0f
    private var downTimeMs = 0L
    private var longPressRunnable: Runnable? = null
    /** Consumes MOVE/UP after a tap on an always-visible radial slot without moving joystick. */
    private var alwaysVisibleRadialSlotTouch = false
    /** Consumes MOVE/UP after tapping outside an idle radial menu to dismiss it. */
    private var radialDismissOnlyTouch = false
    /** Prevents re-firing gesture capture when the finger leaves and re-enters the same slot. */
    private var radialGestureCaptureTriggered = false

    private fun isRadialMenuEngaged(): Boolean =
        session.radialMenuActive.value || session.radialMenuIdle.value

    /**
     * Starts joystick control from an in-flight edge gesture without waiting for a new
     * [MotionEvent.ACTION_DOWN] on this overlay.
     */
    fun beginContinuedGesture(rawX: Float, rawY: Float, downTimeMs: Long) {
        host.onActivity()
        capturing = true
        fromContinuedEdge = true
        movedBeyondTap = true
        longPressTriggered = false
        downRawX = rawX
        downRawY = rawY
        this.downTimeMs = downTimeMs
        lastRawX = rawX
        lastRawY = rawY
        restJoystickX = session.joystickCenterX.floatValue
        restJoystickY = session.joystickCenterY.floatValue

        session.joystickActive.value = true
        session.pointerVisible.value = true
        session.continuedEdgeSessionActive.value = true
        val settings = settingsProvider()
        if (settings.floatingPointerReleaseClickAndDismiss) {
            session.closeRadialMenu()
        }
        session.prepareContinuedEdgeGesture(rawX, rawY, settings)
        val (localX, localY) = host.fingerLocalInTouchOverlay(rawX, rawY)
        downLocalX = localX
        downLocalY = localY
        host.onFingerTrackingMove(rawX, rawY, downLocalX, downLocalY)
        host.captureAllPointers()
        if (settings.floatingPointerHoverEnterSelect) {
            hoverSelectController.begin()
        } else {
            hoverSelectController.cancel()
        }
    }

    /** Forwards MOVE/UP from the edge capture window while the trigger finger stays down. */
    fun forwardContinuedTouch(event: MotionEvent): Boolean {
        if (!capturing) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> handleTouch(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = handleTouch(event)
                capturing = false
                handled
            }
            else -> false
        }
    }

    fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            if (host.shouldSwallowInjectEcho(event)) {
                return true
            }
            val settings = settingsProvider()
            if (settings.floatingPointerHideOnOutsideClick && host.shouldDismissOnOutsideTouch(event)) {
                host.onOutsideDismissPrepare()
            }
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                host.ensureTouchOverlayInteractive()
                val handled = handleTouch(event)
                capturing = handled
                return handled
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!capturing) return false
                val handled = handleTouch(event)
                capturing = false
                return handled
            }
            else -> {
                if (!capturing) return false
                return handleTouch(event)
            }
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        if (host.shouldSwallowInjectEcho(event)) {
            return true
        }
        if (session.gestureReplayActive.value) return true
        if (alwaysVisibleRadialSlotTouch) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                alwaysVisibleRadialSlotTouch = false
                cancelLongPressJob()
                host.releaseAllPointers()
            }
            return true
        }
        if (radialDismissOnlyTouch) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                radialDismissOnlyTouch = false
                cancelLongPressJob()
                host.releaseAllPointers()
                host.onTouchCycleComplete()
            }
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                host.onActivity()
                val settings = settingsProvider()
                val pendingCapture = session.pendingGestureCaptureAction
                if (pendingCapture != null) {
                    session.clearPendingGestureCapture()
                    movedBeyondTap = true
                    longPressTriggered = false
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downLocalX = event.x
                    downLocalY = event.y
                    downTimeMs = System.currentTimeMillis()
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    session.joystickActive.value = true
                    session.pointerVisible.value = true
                    session.beginGesture(event.rawX, event.rawY, settingsProvider())
                    restJoystickX = session.joystickCenterX.floatValue
                    restJoystickY = session.joystickCenterY.floatValue
                    session.armGestureCaptureJoystickOffset(event.rawX, event.rawY)
                    host.onFingerTrackingMove(event.rawX, event.rawY, downLocalX, downLocalY)
                    host.captureAllPointers()
                    host.onStartPendingGestureCapture(pendingCapture)
                    return true
                }
                if (settings.floatingPointerRadialAlwaysVisible &&
                    !session.radialMenuActive.value &&
                    !session.radialMenuIdle.value
                ) {
                    val slot = slotIndexAt(event.rawX, event.rawY)
                    if (slot >= 0) {
                        val action = settings.floatingPointerRadialSlotActions.getOrNull(slot)
                        if (action != null && isGestureCaptureLongPressAction(action)) {
                            movedBeyondTap = true
                            longPressTriggered = false
                            downRawX = event.rawX
                            downRawY = event.rawY
                            downLocalX = event.x
                            downLocalY = event.y
                            downTimeMs = System.currentTimeMillis()
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                            session.joystickActive.value = true
                            session.pointerVisible.value = true
                            session.beginGesture(event.rawX, event.rawY, settings)
                            restJoystickX = session.joystickCenterX.floatValue
                            restJoystickY = session.joystickCenterY.floatValue
                            session.armGestureCaptureJoystickOffset(event.rawX, event.rawY)
                            host.onFingerTrackingMove(event.rawX, event.rawY, downLocalX, downLocalY)
                            host.captureAllPointers()
                            host.onHaptic()
                            host.onRadialMenuAction(slot, fingerStillDown = true)
                            return true
                        }
                        alwaysVisibleRadialSlotTouch = true
                        movedBeyondTap = false
                        longPressTriggered = false
                        if (action != null && action !is GestureAction.None) {
                            host.onHaptic()
                            host.onRadialMenuAction(slot, fingerStillDown = true)
                        }
                        host.onTouchCycleComplete()
                        return true
                    }
                }
                if (isRadialMenuEngaged()) {
                    val slot = slotIndexAt(event.rawX, event.rawY)
                    if (slot >= 0) {
                        val action = settingsProvider().floatingPointerRadialSlotActions.getOrNull(slot)
                        session.closeRadialMenu()
                        host.onRadialMenuClosed()
                        session.joystickActive.value = false
                        host.onGestureEnd(restJoystickX, restJoystickY, false)
                        if (action != null && action !is GestureAction.None) {
                            if (isGestureCaptureLongPressAction(action)) {
                                session.armGestureCaptureJoystickOffset(event.rawX, event.rawY)
                            }
                            host.onRadialMenuAction(slot, fingerStillDown = true)
                        }
                        host.onTouchCycleComplete()
                    } else {
                        radialDismissOnlyTouch = true
                        movedBeyondTap = false
                        session.closeRadialMenu()
                        host.onRadialMenuClosed()
                        session.joystickActive.value = false
                        host.onGestureEnd(restJoystickX, restJoystickY, false)
                        host.onTouchCycleComplete()
                    }
                    return true
                }

                radialGestureCaptureTriggered = false
                movedBeyondTap = false
                longPressTriggered = false
                downRawX = event.rawX
                downRawY = event.rawY
                downLocalX = event.x
                downLocalY = event.y
                downTimeMs = System.currentTimeMillis()
                lastRawX = event.rawX
                lastRawY = event.rawY
                session.joystickActive.value = true
                session.pointerVisible.value = true
                session.beginGesture(event.rawX, event.rawY, settingsProvider())
                restJoystickX = session.joystickCenterX.floatValue
                restJoystickY = session.joystickCenterY.floatValue
                host.onFingerTrackingMove(event.rawX, event.rawY, downLocalX, downLocalY)
                host.captureAllPointers()
                scheduleLongPress()
            }
            MotionEvent.ACTION_MOVE -> {
                host.onActivity()
                if (isRadialMenuEngaged()) {
                    val settings = settingsProvider()
                    val highlightChanged = session.updateRadialHighlight(
                        event.rawX,
                        event.rawY,
                        settings,
                    )
                    if (highlightChanged) {
                        host.onHaptic()
                    }
                    if (session.radialMenuActive.value &&
                        highlightChanged &&
                        !radialGestureCaptureTriggered &&
                        !session.gestureCaptureActive
                    ) {
                        val slot = session.radialHighlightedSlot.intValue
                        val action = settings.floatingPointerRadialSlotActions.getOrNull(slot)
                        if (action != null && isGestureCaptureLongPressAction(action)) {
                            radialGestureCaptureTriggered = true
                            movedBeyondTap = true
                            session.closeRadialMenu()
                            host.onRadialMenuClosed()
                            session.syncPointerForGestureCapture(event.rawX, event.rawY)
                            session.armGestureCaptureJoystickOffset(event.rawX, event.rawY)
                            host.onRadialMenuAction(slot, fingerStillDown = true)
                        }
                    }
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    return true
                }
                val settings = settingsProvider()
                val clickDistancePx = session.clickDistanceThresholdPx(settings)
                val totalDx = event.rawX - downRawX
                val totalDy = event.rawY - downRawY
                if (!movedBeyondTap &&
                    !session.gestureCaptureActive &&
                    hypot(totalDx.toDouble(), totalDy.toDouble()) > clickDistancePx
                ) {
                    cancelLongPressJob()
                    movedBeyondTap = true
                }

                val (joystickX, joystickY) = if (session.gestureCaptureActive) {
                    session.gestureCaptureJoystickCenterForFinger(event.rawX, event.rawY)
                } else {
                    session.joystickCenterForFinger(event.rawX, event.rawY)
                }
                session.joystickCenterX.floatValue = joystickX
                session.joystickCenterY.floatValue = joystickY
                host.onFingerTrackingMove(event.rawX, event.rawY, downLocalX, downLocalY)
                session.applyPointerFromTouch(event.rawX, event.rawY, settings)
                host.onPointerPositionChanged(session.pointerX.floatValue, session.pointerY.floatValue)
                if (fromContinuedEdge && settings.floatingPointerHoverEnterSelect) {
                    hoverSelectController.onPointerMoved(
                        pointerX = session.pointerX.floatValue,
                        pointerY = session.pointerY.floatValue,
                        density = session.density,
                    )
                }
                lastRawX = event.rawX
                lastRawY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                host.onActivity()
                cancelLongPressJob()
                host.releaseAllPointers()
                if (session.gestureCaptureActive) {
                    fromContinuedEdge = false
                    session.continuedEdgeSessionActive.value = false
                    hoverSelectController.cancel()
                    session.dockJoystickAfterGestureCapture(event.rawX, event.rawY)
                    session.joystickActive.value = false
                    session.finishGestureRecorder()
                    session.finishRealtimeGesture()
                    radialGestureCaptureTriggered = false
                    host.onTouchCycleComplete()
                    return true
                }
                if (session.radialMenuActive.value) {
                    fromContinuedEdge = false
                    session.continuedEdgeSessionActive.value = false
                    hoverSelectController.cancel()
                    val slot = session.radialHighlightedSlot.intValue
                    if (slot < 0) {
                        session.closeRadialMenu()
                        host.onRadialMenuClosed()
                        session.joystickActive.value = false
                        host.onGestureEnd(restJoystickX, restJoystickY, false)
                        host.onTouchCycleComplete()
                        return true
                    }
                    session.closeRadialMenu()
                    host.onRadialMenuClosed()
                    session.joystickActive.value = false
                    host.onGestureEnd(restJoystickX, restJoystickY, false)
                    if (!radialGestureCaptureTriggered) {
                        host.onRadialMenuAction(slot, fingerStillDown = false)
                    }
                    radialGestureCaptureTriggered = false
                    host.onTouchCycleComplete()
                    return true
                }
                val settings = settingsProvider()
                if (fromContinuedEdge) {
                    handleContinuedEdgeUp(settings)
                    return true
                }
                val isTap = !movedBeyondTap && !longPressTriggered
                if (settings.floatingPointerHideWhenJoystickReleased) {
                    session.pointerVisible.value = false
                    if (!session.gestureCaptureActive && movedBeyondTap) {
                        session.clearTrail()
                    }
                }
                session.joystickActive.value = false
                val endX = if (movedBeyondTap) {
                    session.joystickCenterX.floatValue
                } else {
                    restJoystickX
                }
                val endY = if (movedBeyondTap) {
                    session.joystickCenterY.floatValue
                } else {
                    restJoystickY
                }
                if (!movedBeyondTap) {
                    session.joystickCenterX.floatValue = restJoystickX
                    session.joystickCenterY.floatValue = restJoystickY
                }

                val elapsed = System.currentTimeMillis() - downTimeMs
                val fingerDistance = hypot(
                    (event.rawX - downRawX).toDouble(),
                    (event.rawY - downRawY).toDouble(),
                ).toFloat()
                val joystickDistance = if (movedBeyondTap) {
                    hypot(
                        (endX - restJoystickX).toDouble(),
                        (endY - restJoystickY).toDouble(),
                    ).toFloat()
                } else {
                    0f
                }
                val swipeDistance = maxOf(fingerDistance, joystickDistance)
                if (settings.floatingPointerHideOnQuickSwipe &&
                    movedBeyondTap &&
                    swipeDistance >= QUICK_SWIPE_MIN_DISTANCE_PX &&
                    elapsed <= QUICK_SWIPE_MAX_DURATION_MS
                ) {
                    session.clearTrail()
                    host.onQuickSwipeDismiss()
                    host.onTouchCycleComplete()
                    return true
                }

                host.onGestureEnd(endX, endY, isTap)

                if (isTap) {
                    performPointerClick()
                }
                host.onTouchCycleComplete()
            }
            MotionEvent.ACTION_CANCEL -> {
                host.onActivity()
                cancelLongPressJob()
                host.releaseAllPointers()
                if (fromContinuedEdge) {
                    fromContinuedEdge = false
                    session.continuedEdgeSessionActive.value = false
                    hoverSelectController.cancel()
                    session.joystickActive.value = false
                    host.onGestureEnd(
                        session.joystickCenterX.floatValue,
                        session.joystickCenterY.floatValue,
                        false,
                    )
                    if (settingsProvider().floatingPointerReleaseClickAndDismiss ||
                        settingsProvider().floatingPointerHoverEnterSelect
                    ) {
                        host.onDismiss()
                    }
                    host.onTouchCycleComplete()
                    return true
                }
                if (session.gestureCaptureActive) {
                    session.joystickActive.value = false
                    session.joystickCenterX.floatValue = restJoystickX
                    session.joystickCenterY.floatValue = restJoystickY
                    session.finishGestureRecorder()
                    session.finishRealtimeGesture()
                    host.onTouchCycleComplete()
                    return true
                }
                if (isRadialMenuEngaged()) {
                    session.closeRadialMenu()
                    host.onRadialMenuClosed()
                }
                if (settingsProvider().floatingPointerHideWhenJoystickReleased) {
                    session.pointerVisible.value = false
                    if (movedBeyondTap) {
                        session.clearTrail()
                    }
                }
                session.joystickActive.value = false
                session.joystickCenterX.floatValue = restJoystickX
                session.joystickCenterY.floatValue = restJoystickY
                host.onGestureEnd(restJoystickX, restJoystickY, false)
                host.onTouchCycleComplete()
            }
        }
        return true
    }

    private fun handleContinuedEdgeUp(settings: AppSettings) {
        fromContinuedEdge = false
        session.joystickActive.value = false
        val endX = session.joystickCenterX.floatValue
        val endY = session.joystickCenterY.floatValue
        host.onGestureEnd(endX, endY, false)

        if (settings.floatingPointerHoverEnterSelect && hoverSelectController.hasPickIntent) {
            session.continuedEdgeSessionActive.value = false
            hoverSelectController.finishAndSubmit(host.hostContext(), settings)
            host.onDismiss()
            host.onTouchCycleComplete()
            return
        }
        hoverSelectController.cancel()

        if (settings.floatingPointerReleaseClickAndDismiss) {
            // Keep continuedEdgeSessionActive until dismissImmediate clears it — prevents
            // always-visible radial from animating back in while the tap is in flight.
            performPointerClickAndDismiss()
            host.onTouchCycleComplete()
            return
        }

        session.continuedEdgeSessionActive.value = false
        if (settings.floatingPointerHideWhenJoystickReleased) {
            session.pointerVisible.value = false
            session.clearTrail()
        }
        host.onTouchCycleComplete()
    }

    private fun performPointerClick() {
        cancelLongPressJob()
        val clickX = session.pointerX.floatValue
        val clickY = session.pointerY.floatValue
        val settings = settingsProvider()
        if (settings.floatingPointerClickHapticEnabled) {
            host.onHaptic()
        }
        if (settings.floatingPointerClickVisualFeedbackEnabled) {
            session.triggerRipple(clickX, clickY)
        }
        session.triggerPointerClick()
        // Inject synchronously so touch-overlay passthrough stays enabled through onTouchCycleComplete.
        host.onPointerClick(clickX, clickY)
    }

    private fun performPointerClickAndDismiss() {
        cancelLongPressJob()
        val clickX = session.pointerX.floatValue
        val clickY = session.pointerY.floatValue
        val settings = settingsProvider()
        // Skip ripple / pointer-click anim — chrome is detached immediately for one-shot.
        if (settings.floatingPointerClickHapticEnabled) {
            host.onHaptic()
        }
        host.onPointerClickAndDismiss(clickX, clickY)
    }

    private fun scheduleLongPress() {
        cancelLongPressJob()
        val settings = settingsProvider()
        val longPressAction = settings.floatingPointerJoystickLongPressAction
        if (longPressAction is GestureAction.OpenFloatingPointerRadialMenu) {
            val delayMs = settings.floatingPointerRadialLongPressMs.coerceIn(200, 2000).toLong()
            val runnable = Runnable {
                if (!movedBeyondTap && !session.radialMenuActive.value && !session.radialMenuIdle.value) {
                    longPressTriggered = true
                    host.onHaptic()
                    session.openRadialMenu(restJoystickX, restJoystickY)
                    host.onRadialMenuOpened()
                    session.updateRadialHighlight(lastRawX, lastRawY, settingsProvider())
                }
            }
            longPressRunnable = runnable
            mainHandler.postDelayed(runnable, delayMs)
            return
        }
        val runnable = Runnable {
            val isGestureCapture = isGestureCaptureLongPressAction(longPressAction)
            // QC: long-tap must complete without crossing the click slop first.
                if (!movedBeyondTap) {
                    longPressTriggered = true
                    if (!isGestureCapture) {
                        session.joystickActive.value = false
                    }
                    if (longPressAction is GestureAction.None) {
                        host.onDismiss()
                    } else {
                        host.onHaptic()
                        if (isGestureCapture) {
                            session.armGestureCaptureJoystickOffset(lastRawX, lastRawY)
                        }
                        host.onJoystickLongPressAction(longPressAction)
                    }
                }
        }
        longPressRunnable = runnable
        val delayMs = if (isGestureCaptureLongPressAction(longPressAction)) {
            settings.floatingPointerRadialLongPressMs.coerceIn(200, 2000).toLong()
        } else {
            LONG_PRESS_DISMISS_MS
        }
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun slotIndexAt(fingerX: Float, fingerY: Float): Int {
        val settings = settingsProvider()
        val inner = settings.floatingPointerRadialInnerDiameterPx / 2f
        val outer = settings.floatingPointerRadialOuterDiameterPx / 2f
        val (centerX, centerY) = session.radialMenuCenterForInput()
        return FloatingPointerRadialMenu.sectorIndexAt(
            centerX = centerX,
            centerY = centerY,
            fingerX = fingerX,
            fingerY = fingerY,
            innerRadius = inner,
            outerRadius = outer,
        ) ?: -1
    }

    private fun cancelLongPressJob() {
        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun isGestureCaptureLongPressAction(action: GestureAction): Boolean =
        action is GestureAction.PointerGestureRecorder ||
            action is GestureAction.PointerRealtimeGesture

    companion object {
        private const val LONG_PRESS_DISMISS_MS = 900L
        private const val QUICK_SWIPE_MIN_DISTANCE_PX = 180f
        private const val QUICK_SWIPE_MAX_DURATION_MS = 450L
    }
}
