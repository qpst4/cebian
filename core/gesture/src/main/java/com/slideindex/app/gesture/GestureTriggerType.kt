package com.slideindex.app.gesture

enum class GestureTriggerType(val id: Int, val isLongDistance: Boolean) {
    SHORT_SWIPE_IN(0, false),
    SHORT_SWIPE_UP_RIGHT(1, false),
    SHORT_SWIPE_DOWN_RIGHT(2, false),
    SHORT_SWIPE_UP(3, false),
    SHORT_SWIPE_DOWN(4, false),
    SHORT_LONG_PRESS(5, false),
    SHORT_SINGLE_TAP(6, false),
    SHORT_SWIPE_IN_UP(7, false),
    SHORT_SWIPE_IN_DOWN(8, false),
    SHORT_SWIPE_IN_AND_BACK(9, false),
    LONG_SWIPE_IN(10, true),
    LONG_SWIPE_UP_RIGHT(11, true),
    LONG_SWIPE_DOWN_RIGHT(12, true),
    LONG_SWIPE_UP(13, true),
    LONG_SWIPE_DOWN(14, true),
    LONG_LONG_PRESS(15, true),
    LONG_SINGLE_TAP(16, true),
    LONG_SWIPE_IN_UP(17, true),
    LONG_SWIPE_IN_DOWN(18, true),
    SHORT_SWIPE_IN_HOVER(19, false),
    SHORT_SWIPE_UP_RIGHT_HOVER(20, false),
    SHORT_SWIPE_DOWN_RIGHT_HOVER(21, false),
    SHORT_SWIPE_UP_HOVER(22, false),
    SHORT_SWIPE_DOWN_HOVER(23, false),
    SHORT_DOUBLE_TAP(24, false),
    /** 沿边首段（上/下）后再向内侧滑；顶/底触钮上「上/下」即沿边左/右。 */
    SHORT_SWIPE_UP_IN(25, false),
    SHORT_SWIPE_DOWN_IN(26, false),
    LONG_SWIPE_UP_IN(27, true),
    LONG_SWIPE_DOWN_IN(28, true),
    /** 沿边首段（上/下）后原路折返。 */
    SHORT_SWIPE_UP_AND_BACK(29, false),
    SHORT_SWIPE_DOWN_AND_BACK(30, false),
    ;

    val isReturnSwipe: Boolean
        get() = this == SHORT_SWIPE_IN_AND_BACK ||
            this == SHORT_SWIPE_UP_AND_BACK ||
            this == SHORT_SWIPE_DOWN_AND_BACK

    val isHoverSwipe: Boolean
        get() = this == SHORT_SWIPE_IN_HOVER ||
            this == SHORT_SWIPE_UP_RIGHT_HOVER ||
            this == SHORT_SWIPE_DOWN_RIGHT_HOVER ||
            this == SHORT_SWIPE_UP_HOVER ||
            this == SHORT_SWIPE_DOWN_HOVER

    val isCornerSwipe: Boolean
        get() = this == SHORT_SWIPE_IN_UP || this == SHORT_SWIPE_IN_DOWN ||
            this == LONG_SWIPE_IN_UP || this == LONG_SWIPE_IN_DOWN ||
            this == SHORT_SWIPE_UP_IN || this == SHORT_SWIPE_DOWN_IN ||
            this == LONG_SWIPE_UP_IN || this == LONG_SWIPE_DOWN_IN

    val isCompoundSwipe: Boolean
        get() = isCornerSwipe || isReturnSwipe

    val supportsIndex: Boolean
        get() = this == SHORT_SWIPE_UP || this == SHORT_SWIPE_DOWN ||
            this == LONG_SWIPE_UP || this == LONG_SWIPE_DOWN

    val isSingleTap: Boolean
        get() = this == SHORT_SINGLE_TAP || this == LONG_SINGLE_TAP

    val isDoubleTap: Boolean
        get() = this == SHORT_DOUBLE_TAP

    val isPressOrTap: Boolean
        get() = this == SHORT_LONG_PRESS || this == SHORT_SINGLE_TAP ||
            this == LONG_LONG_PRESS || this == LONG_SINGLE_TAP

    val isLongPress: Boolean
        get() = this == SHORT_LONG_PRESS || this == LONG_LONG_PRESS

    companion object {
        fun fromId(id: Int): GestureTriggerType? = entries.firstOrNull { it.id == id }

        fun shortDistanceEntries(): List<GestureTriggerType> =
            entries.filter {
                !it.isLongDistance && !it.isPressOrTap && !it.isHoverSwipe && !it.isCompoundSwipe
            }

        fun compoundGestureEntries(): List<GestureTriggerType> = listOf(
            SHORT_SWIPE_IN_UP,
            SHORT_SWIPE_IN_DOWN,
            SHORT_SWIPE_IN_AND_BACK,
            LONG_SWIPE_IN_UP,
            LONG_SWIPE_IN_DOWN,
            SHORT_SWIPE_UP_IN,
            SHORT_SWIPE_DOWN_IN,
            SHORT_SWIPE_UP_AND_BACK,
            SHORT_SWIPE_DOWN_AND_BACK,
            LONG_SWIPE_UP_IN,
            LONG_SWIPE_DOWN_IN,
        )

        fun hoverSwipeEntries(): List<GestureTriggerType> =
            entries.filter { it.isHoverSwipe }

        fun pressTapEntries(): List<GestureTriggerType> =
            listOf(SHORT_LONG_PRESS, SHORT_SINGLE_TAP, SHORT_DOUBLE_TAP)

        fun longDistanceEntries(): List<GestureTriggerType> =
            entries.filter { it.isLongDistance && !it.isPressOrTap && !it.isCompoundSwipe }
    }
}
