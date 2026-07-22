package com.slideindex.app.settings

object ClipboardHistoryCapacity {
    const val UNLIMITED = -1

    val presets: List<Int> = listOf(1, 20, 100, 2000, UNLIMITED)

    fun isValid(value: Int): Boolean = value == UNLIMITED || value in presets

    fun coerce(value: Int): Int =
        if (value == UNLIMITED || value in presets) value else 100
}
