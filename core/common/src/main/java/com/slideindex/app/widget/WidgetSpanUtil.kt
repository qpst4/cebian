package com.slideindex.app.widget

import android.appwidget.AppWidgetProviderInfo
import android.os.Build

object WidgetSpanUtil {
    private const val CELL_SIZE_DP = 70
    private const val CELL_PADDING_DP = 30

    fun spanFromProviderInfo(info: AppWidgetProviderInfo): Pair<Int, Int> {
        val fromMin = dpToSpan(info.minWidth) to dpToSpan(info.minHeight)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val targetX = info.targetCellWidth
            val targetY = info.targetCellHeight
            val spanX = if (targetX > 0) maxOf(targetX, fromMin.first) else fromMin.first
            val spanY = if (targetY > 0) maxOf(targetY, fromMin.second) else fromMin.second
            return spanX.coerceAtLeast(1) to spanY.coerceAtLeast(1)
        }
        return fromMin
    }

    fun dpToSpan(sizeDp: Int): Int =
        ((sizeDp + CELL_PADDING_DP) / CELL_SIZE_DP).coerceAtLeast(1)
}
