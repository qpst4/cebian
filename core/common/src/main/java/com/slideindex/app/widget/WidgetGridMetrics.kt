package com.slideindex.app.widget

object WidgetGridMetrics {
    fun computeGridStepPx(innerWidthPx: Int, columnCount: Int): Int {
        if (columnCount <= 0) return 1
        return (innerWidthPx / columnCount).coerceAtLeast(1)
    }
}
