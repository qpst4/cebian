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
    GestureTriggerType.SHORT_SWIPE_UP_IN -> GestureTriggerType.LONG_SWIPE_UP_IN
    GestureTriggerType.SHORT_SWIPE_DOWN_IN -> GestureTriggerType.LONG_SWIPE_DOWN_IN
    else -> null
}

/**
 * 同一条组合路径的短滑/长滑档位（短档在前）。折返只有短滑版，返回单档。
 * 用于设置页「一条路径一行 + 行内切档」。
 */
fun GestureTriggerType.compoundTierTriggers(): List<GestureTriggerType> = when (this) {
    GestureTriggerType.SHORT_SWIPE_IN_UP, GestureTriggerType.LONG_SWIPE_IN_UP ->
        listOf(GestureTriggerType.SHORT_SWIPE_IN_UP, GestureTriggerType.LONG_SWIPE_IN_UP)
    GestureTriggerType.SHORT_SWIPE_IN_DOWN, GestureTriggerType.LONG_SWIPE_IN_DOWN ->
        listOf(GestureTriggerType.SHORT_SWIPE_IN_DOWN, GestureTriggerType.LONG_SWIPE_IN_DOWN)
    GestureTriggerType.SHORT_SWIPE_UP_IN, GestureTriggerType.LONG_SWIPE_UP_IN ->
        listOf(GestureTriggerType.SHORT_SWIPE_UP_IN, GestureTriggerType.LONG_SWIPE_UP_IN)
    GestureTriggerType.SHORT_SWIPE_DOWN_IN, GestureTriggerType.LONG_SWIPE_DOWN_IN ->
        listOf(GestureTriggerType.SHORT_SWIPE_DOWN_IN, GestureTriggerType.LONG_SWIPE_DOWN_IN)
    else -> listOf(this)
}

fun GestureTriggerType.opensFromInwardSwipeHub(): Boolean =
    directionFamily() == SwipeDirectionFamily.IN
