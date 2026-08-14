package com.slideindex.app.settings

object ClipboardFloatWindowMetrics {
    const val UNSET_POSITION = -1

    const val MIN_WIDTH_DP = 180
    const val MAX_WIDTH_DP = 600
    const val DEFAULT_WIDTH_DP = 320

    const val MIN_HEIGHT_DP = 160
    const val MAX_HEIGHT_DP = 720
    const val DEFAULT_HEIGHT_DP = 280

    const val COLUMN_ONE_MAX_CONTENT_DP = 260
    const val COLUMN_TWO_MAX_CONTENT_DP = 420

    const val PAGE_SIZE = 40

    fun coerceWidth(value: Int): Int = value.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)

    fun coerceHeight(value: Int): Int = value.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)

    fun columnCount(contentWidthDp: Int): Int = when {
        contentWidthDp < COLUMN_ONE_MAX_CONTENT_DP -> 1
        contentWidthDp < COLUMN_TWO_MAX_CONTENT_DP -> 2
        else -> 3
    }
}
