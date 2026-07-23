package com.slideindex.app.message

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

fun Modifier.messageGestureActions(
    gestureKey: Any,
    settings: MessageSettings,
    onAction: (MessageAction) -> Unit,
    onLongPressMenu: (() -> Unit)? = null,
    onLongPressHaptic: (() -> Unit)? = null,
): Modifier = pointerInput(gestureKey, settings, onLongPressMenu, onLongPressHaptic) {
    val touchSlop = viewConfiguration.touchSlop
    val swipeThreshold = maxOf(MESSAGE_GESTURE_SWIPE_THRESHOLD_PX, touchSlop)
    val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
    val gestureJobScope = CoroutineScope(coroutineContext)

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val pointerId = down.id
        var totalX = 0f
        var totalY = 0f
        var longPressTriggered = false

        val longPressJob = gestureJobScope.launch {
            delay(longPressTimeoutMs)
            if (abs(totalX) <= touchSlop && abs(totalY) <= touchSlop) {
                longPressTriggered = true
                onLongPressHaptic?.invoke()
                val longPressAction = settings.longPressAction
                if (longPressAction != MessageAction.Ignore) {
                    onAction(longPressAction)
                } else {
                    onLongPressMenu?.invoke()
                }
            }
        }

        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                if (change.pressed) {
                    val delta = change.positionChange()
                    if (delta != Offset.Zero) {
                        change.consume()
                        totalX += delta.x
                        totalY += delta.y
                    }
                    if (!longPressTriggered &&
                        (abs(totalX) > touchSlop || abs(totalY) > touchSlop)
                    ) {
                        longPressJob.cancel()
                    }
                    continue
                }

                if (!longPressTriggered) {
                    val swipeAction = resolveMessageSwipeAction(
                        totalX,
                        totalY,
                        settings,
                        swipeThreshold,
                    )
                    when {
                        swipeAction != null -> onAction(swipeAction)
                        abs(totalX) <= touchSlop && abs(totalY) <= touchSlop ->
                            onAction(settings.singleTapAction)
                    }
                }
                break
            }
        } finally {
            longPressJob.cancel()
        }
    }
}
