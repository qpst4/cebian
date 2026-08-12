package com.slideindex.app.gesture

/**
 * 轮盘 / 蜂窝「命中提示」胶囊的尺寸换算（图标 dp 为基准，文字与高度同比缩放）。
 */
object SelectedHintMetrics {
    const val DEFAULT_ICON_SIZE_DP = 22
    const val MIN_ICON_SIZE_DP = 16
    const val MAX_ICON_SIZE_DP = 40

    @JvmStatic
    fun clampIconSizeDp(value: Int): Int = value.coerceIn(MIN_ICON_SIZE_DP, MAX_ICON_SIZE_DP)

    @JvmStatic
    fun textSizePx(iconSizeDp: Int, density: Float): Float =
        iconSizeDp * 15f / DEFAULT_ICON_SIZE_DP * density

    @JvmStatic
    fun boxHeightPx(iconSizeDp: Int, density: Float): Float =
        iconSizeDp * 36f / DEFAULT_ICON_SIZE_DP * density

    @JvmStatic
    fun paddingXPx(density: Float): Float = 12f * density

    @JvmStatic
    fun gapPx(density: Float): Float = 8f * density

    /** 蜂窝命中提示相对圆盘顶部的额外上移量（dp）。 */
    const val HONEYCOMB_HINT_ABOVE_DISC_DP = 44f

    @JvmStatic
    fun honeycombHintAboveDiscPx(density: Float): Float = HONEYCOMB_HINT_ABOVE_DISC_DP * density
}
