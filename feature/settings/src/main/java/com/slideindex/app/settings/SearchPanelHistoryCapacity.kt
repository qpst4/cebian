package com.slideindex.app.settings

object SearchPanelHistoryCapacity {
    val presets: List<Int> = listOf(10, 20, 50, 100, 200)

    const val DEFAULT = 50

    fun coerce(value: Int): Int =
        if (value in presets) value else DEFAULT
}
