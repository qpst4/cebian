package com.slideindex.app.gesture

enum class GestureTriggerMode(val id: Int) {
    DEFAULT(-1),
    ON_RELEASE(0),
    CONTINUOUS(1),
    IMMEDIATE(2),
    ;

    companion object {
        fun fromId(id: Int): GestureTriggerMode =
            entries.firstOrNull { it.id == id } ?: DEFAULT

        val configurableEntries: List<GestureTriggerMode> =
            listOf(DEFAULT, ON_RELEASE, CONTINUOUS, IMMEDIATE)
    }
}

fun GestureTriggerMode.supportsAction(action: GestureAction, trigger: GestureTriggerType): Boolean =
    when {
        action.requiresContinuousTriggerOnly() ->
            this == GestureTriggerMode.CONTINUOUS && action.supportsContinuousTracking(trigger)
        this == GestureTriggerMode.CONTINUOUS -> action.supportsContinuousTracking(trigger)
        this == GestureTriggerMode.IMMEDIATE -> when {
            action is GestureAction.ClickPassthrough -> false
            trigger.isSingleTap -> false
            else -> true
        }
        this == GestureTriggerMode.DEFAULT -> true
        this == GestureTriggerMode.ON_RELEASE -> true
        else -> false
    }
