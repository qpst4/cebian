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
    val panelColumns = if (appCount in 1 until m) appCount else m
    val rows = if (appCount == 0) 1 else ceil(appCount / m.toFloat()).toInt()
    val panelWidth = panelColumns * cellWidth + gridPadding * 2
    return GridLayoutInfo(m, panelColumns, rows, panelWidth)
}

fun visualColumn(index: Int, m: Int, appCount: Int, side: PanelSide): Int {
    val colInRow = index % m
    return when (side) {
        PanelSide.RIGHT -> m - 1 - colInRow
        PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> colInRow
    }
}
