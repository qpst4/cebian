package com.slideindex.app.overlay.layout

import com.slideindex.app.overlay.PanelSide
import kotlin.math.ceil

data class GridLayoutInfo(
    val appsPerRow: Int,
    val panelColumns: Int,
    val rows: Int,
    val panelWidth: Float,
)

fun gridLayoutInfo(appCount: Int, appsPerRow: Int, cellWidth: Float, gridPadding: Float): GridLayoutInfo {
    val m = appsPerRow
    if (appCount <= 0) {
        return GridLayoutInfo(m, 0, 0, 0f)
    }
    val panelColumns = minOf(appCount, m)
    val rows = ceil(appCount / m.toFloat()).toInt()
    val panelWidth = panelColumns * cellWidth + gridPadding * 2
    return GridLayoutInfo(m, panelColumns, rows, panelWidth)
}

fun visualColumn(index: Int, m: Int, appCount: Int, side: PanelSide): Int {
    val colInRow = index % m
    val panelColumns = minOf(appCount, m)
    return when (side) {
        PanelSide.RIGHT -> panelColumns - 1 - colInRow
        PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> colInRow
    }
}
