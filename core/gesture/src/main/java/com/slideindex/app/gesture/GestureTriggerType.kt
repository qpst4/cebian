package com.slideindex.app.gesture

enum class GestureTriggerType(val id: Int, val isLongDistance: Boolean) {
    SHORT_SWIPE_IN(0, false),
    SHORT_SWIPE_UP_RIGHT(1, false),
    SHORT_SWIPE_DOWN_RIGHT(2, false),
    SHORT_SWIPE_UP(3, false),
    SHORT_SWIPE_DOWN(4, false),
    SHORT_LONG_PRESS(5, false),
    SHORT_SINGLE_TAP(6, false),
    LONG_SWIPE_IN(10, true),
    LONG_SWIPE_UP_RIGHT(11, true),
    LONG_SWIPE_DOWN_RIGHT(12, true),
    LONG_SWIPE_UP(13, true),
    LONG_SWIPE_DOWN(14, true),
    LONG_LONG_PRESS(15, true),
    LONG_SINGLE_TAP(16, true),
    ;

    val supportsIndex: Boolean
        get() = this == SHORT_SWIPE_UP || this == SHORT_SWIPE_DOWN ||
            this == LONG_SWIPE_UP || this == LONG_SWIPE_DOWN

    val isSingleTap: Boolean
        get() = this == SHORT_SINGLE_TAP || this == LONG_SINGLE_TAP

    val isPressOrTap: Boolean
        get() = this == SHORT_LONG_PRESS || this == SHORT_SINGLE_TAP ||
            this == LONG_LONG_PRESS || this == LONG_SINGLE_TAP

    val isLongPress: Boolean
        get() = this == SHORT_LONG_PRESS || this == LONG_LONG_PRESS

    companion object {
        fun fromId(id: Int): GestureTriggerType? = entries.firstOrNull { it.id == id }

        fun shortDistanceEntries(): List<GestureTriggerType> =
            entries.filter { !it.isLongDistance && !it.isPressOrTap }

        fun pressTapEntries(): List<GestureTriggerType> =
            listOf(SHORT_LONG_PRESS, SHORT_SINGLE_TAP)

        fun longDistanceEntries(): List<GestureTriggerType> =
            entries.filter { it.isLongDistance && !it.isPressOrTap }

        private val GestureTriggerType.isPressOrTap: Boolean
            get() = this.isPressOrTap
    }
}
