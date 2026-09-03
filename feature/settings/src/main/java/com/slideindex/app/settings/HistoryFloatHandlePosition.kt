package com.slideindex.app.settings

object HistoryFloatHandlePosition {
    const val UNSET_POSITION = ClipboardFloatWindowMetrics.UNSET_POSITION

    /** 与旧版 CENTER_VERTICAL + y=-height/3 视觉接近的默认距顶偏移。 */
    fun defaultY(screenHeightPx: Int): Int = screenHeightPx / 6

    fun resolveY(storedY: Int, screenHeightPx: Int, handleHeightPx: Int): Int {
        val raw = if (storedY == UNSET_POSITION) defaultY(screenHeightPx) else storedY
        return clampY(raw, screenHeightPx, handleHeightPx)
    }

    fun clampY(y: Int, screenHeightPx: Int, handleHeightPx: Int): Int {
        val maxY = (screenHeightPx - handleHeightPx).coerceAtLeast(0)
        return y.coerceIn(0, maxY)
    }
}
