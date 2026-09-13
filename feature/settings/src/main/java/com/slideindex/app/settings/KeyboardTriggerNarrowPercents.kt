package com.slideindex.app.settings

object KeyboardTriggerNarrowPercents {
    const val DEFAULT = 50
    const val MIN = 1
    const val MAX = 99

    fun coerce(percent: Int): Int = percent.coerceIn(MIN, MAX)

    fun scale(percent: Int): Float = coerce(percent) / 100f
}

fun AppSettings.keyboardTriggerNarrowPercent(isLandscape: Boolean): Int =
    if (isLandscape) {
        edgeTrigger.keyboardTriggerNarrowPercentLandscape
    } else {
        edgeTrigger.keyboardTriggerNarrowPercentPortrait
    }

fun AppSettings.keyboardTriggerNarrowScale(isLandscape: Boolean): Float =
    KeyboardTriggerNarrowPercents.scale(keyboardTriggerNarrowPercent(isLandscape))
