package com.slideindex.app.message

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

fun Modifier.messageGestureActions(
    gestureKey: Any,
    settings: MessageSettings,
    onAction: (MessageAction) -> Unit,
    onLongPress: (() -> Unit)? = null,
): Modifier = pointerInput(gestureKey, settings, onLongPress) {
    val touchSlop = viewConfiguration.touchSlop
    val swipeThreshold = maxOf(MESSAGE_GESTURE_SWIPE_THRESHOLD_PX, touchSlop)

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downTime = System.currentTimeMillis()
        var totalX = 0f
        var totalY = 0f
        var pointerId = down.id

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (!change.pressed) {
                val heldMs = System.currentTimeMillis() - downTime
                if (onLongPress != null &&
                    heldMs >= viewConfiguration.longPressTimeoutMillis &&
                    abs(totalX) <= touchSlop &&
                    abs(totalY) <= touchSlop
                ) {
                    onLongPress()
                    break
                }
                val swipeAction = resolveMessageSwipeAction(totalX, totalY, settings, swipeThreshold)
                when {
                    swipeAction != null -> onAction(swipeAction)
                    abs(totalX) <= touchSlop && abs(totalY) <= touchSlop ->
                        onAction(settings.singleTapAction)
                }
                break
            }
            val delta = change.positionChange()
            if (delta != Offset.Zero) {
                change.consume()
                totalX += delta.x
                totalY += delta.y
            }
        }
    }
}
