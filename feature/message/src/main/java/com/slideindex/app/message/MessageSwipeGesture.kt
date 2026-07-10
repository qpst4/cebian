package com.slideindex.app.message

import kotlin.math.abs

const val MESSAGE_GESTURE_SWIPE_THRESHOLD_PX = 80f

fun resolveMessageSwipeAction(
    totalX: Float,
    totalY: Float,
    settings: MessageSettings,
    thresholdPx: Float = MESSAGE_GESTURE_SWIPE_THRESHOLD_PX,
): MessageAction? = when {
    abs(totalY) > abs(totalX) && totalY < -thresholdPx -> settings.swipeUpAction
    abs(totalY) > abs(totalX) && totalY > thresholdPx -> settings.swipeDownAction
    totalX < -thresholdPx -> settings.swipeLeftAction
    totalX > thresholdPx -> settings.swipeRightAction
    else -> null
}
