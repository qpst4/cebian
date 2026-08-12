package com.slideindex.app.settings

object HistoryFloatHandleWidth {
    const val MIN_DP = 24
    const val MAX_DP = 50
    const val DEFAULT_DP = 32

    val presets: List<Int> = listOf(24, 28, 32, 36, 40, 44, 50)

    fun coerce(value: Int): Int = value.coerceIn(MIN_DP, MAX_DP)
}
