package com.slideindex.app.message

enum class SideBubbleHorizontalEdge(val id: String) {
    Left("left"),
    Right("right"),
    ;

    companion object {
        fun fromId(id: String?): SideBubbleHorizontalEdge =
            entries.firstOrNull { it.id == id } ?: Right
    }
}

enum class SideBubbleVerticalAnchor(val id: String) {
    Middle("middle"),
    Bottom("bottom"),
    ;

    companion object {
        fun fromId(id: String?): SideBubbleVerticalAnchor =
            entries.firstOrNull { it.id == id } ?: Middle

        fun defaultYFraction(anchor: SideBubbleVerticalAnchor): Float = when (anchor) {
            Middle -> 0.5f
            Bottom -> 0.78f
        }
    }
}

enum class MessageOverlayCorner(val id: String) {
    TopStart("top_start"),
    TopEnd("top_end"),
    BottomStart("bottom_start"),
    BottomEnd("bottom_end"),
    ;

    fun horizontalEdge(): SideBubbleHorizontalEdge = when (this) {
        TopStart, BottomStart -> SideBubbleHorizontalEdge.Left
        TopEnd, BottomEnd -> SideBubbleHorizontalEdge.Right
    }

    fun defaultYFraction(): Float = when (this) {
        TopStart, TopEnd -> MessagePlacementFractions.DEFAULT_TOP_Y
        BottomStart, BottomEnd -> MessagePlacementFractions.DEFAULT_BOTTOM_Y
    }

    companion object {
        fun fromId(id: String?): MessageOverlayCorner =
            entries.firstOrNull { it.id == id } ?: BottomEnd
    }
}

object MessagePlacementFractions {
    const val MIN_Y = 0.12f
    const val MAX_Y = 0.88f
    const val DEFAULT_TOP_Y = 0.15f
    const val DEFAULT_BOTTOM_Y = 0.85f
    const val SIDE_BOTTOM_Y = 0.78f

    fun coerceY(fraction: Float): Float = fraction.coerceIn(MIN_Y, MAX_Y)
}

object DanmakuSpeed {
    const val SLOW = 0
    const val NORMAL = 1
    const val FAST = 2

    fun durationMs(level: Int): Long = when (level.coerceIn(SLOW, FAST)) {
        SLOW -> 8_000L
        FAST -> 3_500L
        else -> 5_500L
    }
}

object SideBubbleFontSize {
    const val SMALL = 0
    const val NORMAL = 1
    const val LARGE = 2

    fun coerce(level: Int): Int = level.coerceIn(SMALL, LARGE)
}
