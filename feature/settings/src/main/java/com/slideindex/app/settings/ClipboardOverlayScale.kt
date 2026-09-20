package com.slideindex.app.settings

object ClipboardOverlayScale {
    const val MIN_PERCENT = 50
    const val MAX_PERCENT = 100
    const val DEFAULT_PERCENT = 100

    fun coerce(value: Int): Int = value.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun toFactor(percent: Int): Float = coerce(percent) / 100f
}
