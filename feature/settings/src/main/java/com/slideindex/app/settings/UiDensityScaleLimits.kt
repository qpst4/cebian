package com.slideindex.app.settings

object UiDensityScaleLimits {
    const val MIN_SCALE = 0.8f
    const val MAX_SCALE = 1.1f
    const val DEFAULT_SCALE = 1f
    const val MIN_PERCENT = 80
    const val MAX_PERCENT = 110
    val KEY_POINT_PERCENTS = listOf(80f, 90f, 100f, 110f)

    fun normalize(scale: Float): Float =
        if (scale.isFinite()) scale.coerceIn(MIN_SCALE, MAX_SCALE) else DEFAULT_SCALE

    fun scaleToPercent(scale: Float): Float = normalize(scale) * 100f

    fun percentToScale(percent: Float): Float = normalize(percent / 100f)
}
