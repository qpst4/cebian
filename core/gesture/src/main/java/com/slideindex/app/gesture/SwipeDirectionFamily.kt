package com.slideindex.app.gesture

enum class SwipeDirectionFamily {
    IN,
    UP_RIGHT,
    DOWN_RIGHT,
    UP,
    DOWN,
    ;

    val shortTrigger: GestureTriggerType
        get() = when (this) {
            IN -> GestureTriggerType.SHORT_SWIPE_IN
            UP_RIGHT -> GestureTriggerType.SHORT_SWIPE_UP_RIGHT
            DOWN_RIGHT -> GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT
            UP -> GestureTriggerType.SHORT_SWIPE_UP
            DOWN -> GestureTriggerType.SHORT_SWIPE_DOWN
        }

    val longTrigger: GestureTriggerType
        get() = when (this) {
            IN -> GestureTriggerType.LONG_SWIPE_IN
            UP_RIGHT -> GestureTriggerType.LONG_SWIPE_UP_RIGHT
            DOWN_RIGHT -> GestureTriggerType.LONG_SWIPE_DOWN_RIGHT
            UP -> GestureTriggerType.LONG_SWIPE_UP
            DOWN -> GestureTriggerType.LONG_SWIPE_DOWN
        }

    val hoverTrigger: GestureTriggerType
        get() = when (this) {
            IN -> GestureTriggerType.SHORT_SWIPE_IN_HOVER
            UP_RIGHT -> GestureTriggerType.SHORT_SWIPE_UP_RIGHT_HOVER
            DOWN_RIGHT -> GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT_HOVER
            UP -> GestureTriggerType.SHORT_SWIPE_UP_HOVER
            DOWN -> GestureTriggerType.SHORT_SWIPE_DOWN_HOVER
        }

    val hasAfterPauseBranches: Boolean
        get() = this == IN

    companion object {
        fun orderedEntries(): List<SwipeDirectionFamily> =
            listOf(IN, UP_RIGHT, DOWN_RIGHT, UP, DOWN)

        fun fromName(name: String): SwipeDirectionFamily? =
            entries.firstOrNull { it.name == name }
    }
}

fun GestureTriggerType.directionFamily(): SwipeDirectionFamily? = when (this) {
    GestureTriggerType.SHORT_SWIPE_IN,
    GestureTriggerType.LONG_SWIPE_IN,
    GestureTriggerType.SHORT_SWIPE_IN_HOVER,
    GestureTriggerType.SHORT_SWIPE_IN_UP,
    GestureTriggerType.SHORT_SWIPE_IN_DOWN,
    GestureTriggerType.SHORT_SWIPE_IN_AND_BACK,
    GestureTriggerType.LONG_SWIPE_IN_UP,
    GestureTriggerType.LONG_SWIPE_IN_DOWN,
    -> SwipeDirectionFamily.IN

    GestureTriggerType.SHORT_SWIPE_UP_RIGHT,
    GestureTriggerType.LONG_SWIPE_UP_RIGHT,
    GestureTriggerType.SHORT_SWIPE_UP_RIGHT_HOVER,
    -> SwipeDirectionFamily.UP_RIGHT

    GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT,
    GestureTriggerType.LONG_SWIPE_DOWN_RIGHT,
    GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT_HOVER,
    -> SwipeDirectionFamily.DOWN_RIGHT

    GestureTriggerType.SHORT_SWIPE_UP,
    GestureTriggerType.LONG_SWIPE_UP,
    GestureTriggerType.SHORT_SWIPE_UP_HOVER,
    -> SwipeDirectionFamily.UP

    GestureTriggerType.SHORT_SWIPE_DOWN,
    GestureTriggerType.LONG_SWIPE_DOWN,
    GestureTriggerType.SHORT_SWIPE_DOWN_HOVER,
    -> SwipeDirectionFamily.DOWN

    else -> null
}
