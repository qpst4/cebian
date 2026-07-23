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
    }
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
