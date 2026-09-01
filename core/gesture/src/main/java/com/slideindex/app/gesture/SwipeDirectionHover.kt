package com.slideindex.app.gesture

fun SwipeDirection.toHoverTrigger(): GestureTriggerType? = when (this) {
    SwipeDirection.IN -> GestureTriggerType.SHORT_SWIPE_IN_HOVER
    SwipeDirection.UP_RIGHT -> GestureTriggerType.SHORT_SWIPE_UP_RIGHT_HOVER
    SwipeDirection.DOWN_RIGHT -> GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT_HOVER
    SwipeDirection.UP -> GestureTriggerType.SHORT_SWIPE_UP_HOVER
    SwipeDirection.DOWN -> GestureTriggerType.SHORT_SWIPE_DOWN_HOVER
}

fun SwipeDirection.toBaseShortTrigger(): GestureTriggerType = when (this) {
    SwipeDirection.IN -> GestureTriggerType.SHORT_SWIPE_IN
    SwipeDirection.UP_RIGHT -> GestureTriggerType.SHORT_SWIPE_UP_RIGHT
    SwipeDirection.DOWN_RIGHT -> GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT
    SwipeDirection.UP -> GestureTriggerType.SHORT_SWIPE_UP
    SwipeDirection.DOWN -> GestureTriggerType.SHORT_SWIPE_DOWN
}
