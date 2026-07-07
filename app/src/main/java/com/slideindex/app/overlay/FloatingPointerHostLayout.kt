package com.slideindex.app.overlay

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.hypot

/**
 * Joystick-sized touch target. Touches elsewhere on the screen pass through to apps below.
 * [FLAG_WATCH_OUTSIDE_TOUCH] delivers ACTION_OUTSIDE for optional click-to-hide. Echoes from
 * injected pointer taps carry zeroed coordinates on modern Android, so
 * [FloatingPointerOverlayWindow.shouldDismissOnOutsideTouch] also suppresses dismiss for a
 * short window after each injected tap.
 */
internal class FloatingPointerHostLayout(
    context: Context,
    private val session: FloatingPointerSession,
    private val settingsProvider: () -> com.slideindex.app.settings.AppSettings,
    private val onJoystickPositionChanged: (centerX: Float, centerY: Float) -> Unit,
    private val onGestureEnd: (centerX: Float, centerY: Float, isTap: Boolean) -> Unit,
    private val onPointerClick: (rawX: Float, rawY: Float) -> Unit,
    /** Hide pointer when user touches outside the joystick (does not block that touch). */
    private val onOutsideDismissPrepare: () -> Unit,
    private val onQuickSwipeDismiss: () -> Unit,
    private val onDismiss: () -> Unit,
    private val onRadialMenuOpened: () -> Unit,
    private val onRadialMenuClosed: () -> Unit,
    private val onRadialMenuAction: (slotIndex: Int) -> Unit,
    private val onActivity: () -> Unit,
    private val onHaptic: () -> Unit,
    private val shouldDismissOnOutsideTouch: (MotionEvent) -> Boolean,
    private val onTouchCycleComplete: () -> Unit,
) : FrameLayout(context) {
    init {
        isHapticFeedbackEnabled = false
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var capturing = false
    private var movedBeyondTap = false
    private var longPressTriggered = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var restJoystickX = 0f
    private var restJoystickY = 0f
    private var downTimeMs = 0L
    private var longPressRunnable: Runnable? = null

    /**
     * Starts joystick control from an in-flight edge gesture without waiting for a new
     * [MotionEvent.ACTION_DOWN] on this overlay.
     */
    fun beginContinuedGesture(rawX: Float, rawY: Float, downTimeMs: Long) {
        onActivity()
        capturing = true
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
        session.beginGesture(rawX, rawY, settingsProvider())
        session.joystickCenterX.floatValue = rawX
        session.joystickCenterY.floatValue = rawY
        onJoystickPositionChanged(rawX, rawY)
        captureAllPointers()
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            val settings = settingsProvider()
            if (settings.floatingPointerHideOnOutsideClick && shouldDismissOnOutsideTouch(event)) {
                onOutsideDismissPrepare()
            }
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onActivity()
                movedBeyondTap = false
                longPressTriggered = false
                downRawX = event.rawX
                downRawY = event.rawY
                downTimeMs = System.currentTimeMillis()
                lastRawX = event.rawX
                lastRawY = event.rawY
                restJoystickX = session.joystickCenterX.floatValue
                restJoystickY = session.joystickCenterY.floatValue

                session.joystickActive.value = true
                session.pointerVisible.value = true
                session.beginGesture(event.rawX, event.rawY, settingsProvider())
                captureAllPointers()
                scheduleLongPress()
            }
            MotionEvent.ACTION_MOVE -> {
                onActivity()
                if (session.radialMenuActive.value) {
                    if (session.updateRadialHighlight(event.rawX, event.rawY, settingsProvider())) {
                        onHaptic()
                    }
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    return true
                }
                val totalDx = event.rawX - downRawX
                val totalDy = event.rawY - downRawY
                if (!movedBeyondTap &&
                    hypot(totalDx.toDouble(), totalDy.toDouble()) > session.tapSlopPx
                ) {
                    cancelLongPressJob()
                    movedBeyondTap = true
                }
                if (!movedBeyondTap) return true

                session.joystickCenterX.floatValue = event.rawX
                session.joystickCenterY.floatValue = event.rawY
                onJoystickPositionChanged(event.rawX, event.rawY)
                session.applyPointerFromTouch(event.rawX, event.rawY, settingsProvider())
                lastRawX = event.rawX
                lastRawY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                onActivity()
                cancelLongPressJob()
                releaseAllPointers()
                if (session.radialMenuActive.value) {
                    val slot = session.radialHighlightedSlot.intValue
                    session.closeRadialMenu()
                    onRadialMenuClosed()
                    session.joystickActive.value = false
                    onGestureEnd(restJoystickX, restJoystickY, false)
                    if (slot >= 0) {
                        onRadialMenuAction(slot)
                    }
                    onTouchCycleComplete()
                    return true
                }
                val settings = settingsProvider()
                val isTap = !movedBeyondTap && !longPressTriggered
                if (settings.floatingPointerHideWhenJoystickReleased && movedBeyondTap) {
                    session.pointerVisible.value = false
                    session.clearTrail()
                }
                session.joystickActive.value = false
                val endX = if (movedBeyondTap) event.rawX else restJoystickX
                val endY = if (movedBeyondTap) event.rawY else restJoystickY
                if (!movedBeyondTap) {
                    session.joystickCenterX.floatValue = restJoystickX
                    session.joystickCenterY.floatValue = restJoystickY
                } else {
                    onJoystickPositionChanged(endX, endY)
                }

                val elapsed = System.currentTimeMillis() - downTimeMs
                val swipeDistance = hypot(
                    (event.rawX - downRawX).toDouble(),
                    (event.rawY - downRawY).toDouble(),
                ).toFloat()
                if (settings.floatingPointerHideOnQuickSwipe &&
                    movedBeyondTap &&
                    swipeDistance >= QUICK_SWIPE_MIN_DISTANCE_PX &&
                    elapsed <= QUICK_SWIPE_MAX_DURATION_MS
                ) {
                    session.clearTrail()
                    onQuickSwipeDismiss()
                    onTouchCycleComplete()
                    return true
                }

                onGestureEnd(endX, endY, isTap)

                if (isTap) {
                    performPointerClick()
                }
                onTouchCycleComplete()
            }
            MotionEvent.ACTION_CANCEL -> {
                onActivity()
                cancelLongPressJob()
                releaseAllPointers()
                if (session.radialMenuActive.value) {
                    session.closeRadialMenu()
                    onRadialMenuClosed()
                }
                if (settingsProvider().floatingPointerHideWhenJoystickReleased && movedBeyondTap) {
                    session.pointerVisible.value = false
                    session.clearTrail()
                }
                session.joystickActive.value = false
                session.joystickCenterX.floatValue = restJoystickX
                session.joystickCenterY.floatValue = restJoystickY
                onGestureEnd(restJoystickX, restJoystickY, false)
                onTouchCycleComplete()
            }
        }
        return true
    }

    private fun performPointerClick() {
        cancelLongPressJob()
        onHaptic()
        val clickX = session.pointerX.floatValue
        val clickY = session.pointerY.floatValue
        session.triggerRipple(clickX, clickY)
        onPointerClick(clickX, clickY)
    }

    private fun scheduleLongPress() {
        cancelLongPressJob()
        val settings = settingsProvider()
        if (!settings.floatingPointerRadialMenuEnabled) {
            val runnable = Runnable {
                if (!movedBeyondTap) {
                    longPressTriggered = true
                    session.joystickActive.value = false
                    mainHandler.post { onDismiss() }
                }
            }
            longPressRunnable = runnable
            mainHandler.postDelayed(runnable, LONG_PRESS_DISMISS_MS)
            return
        }
        val delayMs = settings.floatingPointerRadialLongPressMs.coerceIn(200, 2000).toLong()
        val runnable = Runnable {
            if (!movedBeyondTap && !session.radialMenuActive.value) {
                longPressTriggered = true
                onHaptic()
                session.openRadialMenu(restJoystickX, restJoystickY)
                onRadialMenuOpened()
                session.updateRadialHighlight(lastRawX, lastRawY, settingsProvider())
            }
        }
        longPressRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelLongPressJob() {
        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun captureAllPointers() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { requestPointerCapture() }
        }
    }

    private fun releaseAllPointers() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { releasePointerCapture() }
        }
    }

    companion object {
        private const val LONG_PRESS_DISMISS_MS = 900L
        private const val QUICK_SWIPE_MIN_DISTANCE_PX = 240f
        private const val QUICK_SWIPE_MAX_DURATION_MS = 220L
    }
}
