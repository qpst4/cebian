package com.slideindex.app.gesture

enum class InwardCompoundBranch(
    val shortTrigger: GestureTriggerType,
) {
    UP(GestureTriggerType.SHORT_SWIPE_IN_UP),
    DOWN(GestureTriggerType.SHORT_SWIPE_IN_DOWN),
    RETURN(GestureTriggerType.SHORT_SWIPE_IN_AND_BACK),
    ;

    val pairedLongTrigger: GestureTriggerType?
        get() = shortTrigger.pairedLongCornerTrigger()

    companion object {
        fun orderedEntries(): List<InwardCompoundBranch> = listOf(UP, DOWN, RETURN)
    }
}

fun GestureTriggerType.pairedLongCornerTrigger(): GestureTriggerType? = when (this) {
    GestureTriggerType.SHORT_SWIPE_IN_UP -> GestureTriggerType.LONG_SWIPE_IN_UP
    GestureTriggerType.SHORT_SWIPE_IN_DOWN -> GestureTriggerType.LONG_SWIPE_IN_DOWN
    else -> null
}

fun GestureTriggerType.opensFromInwardSwipeHub(): Boolean =
    directionFamily() == SwipeDirectionFamily.IN
