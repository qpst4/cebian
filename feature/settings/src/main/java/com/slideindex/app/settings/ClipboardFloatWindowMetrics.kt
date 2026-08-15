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

    /** 浮窗贴边留白 */
    const val EDGE_MARGIN_DP = 12

    /** chip 默认锚点：在键盘顶缘之上额外抬高，避免被键盘遮挡 */
    const val CHIP_ABOVE_IME_EXTRA_DP = 56

    /** 大窗默认锚点：相对屏幕垂直居中再向上偏移 */
    const val PANEL_DEFAULT_ABOVE_CENTER_DP = 72

    fun coerceWidth(value: Int): Int = value.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)

    fun coerceHeight(value: Int): Int = value.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)

    fun columnCount(contentWidthDp: Int): Int = when {
        contentWidthDp < COLUMN_ONE_MAX_CONTENT_DP -> 1
        contentWidthDp < COLUMN_TWO_MAX_CONTENT_DP -> 2
        else -> 3
    }
}
