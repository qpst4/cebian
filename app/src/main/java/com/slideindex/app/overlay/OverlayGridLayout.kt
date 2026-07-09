package com.slideindex.app.overlay

import kotlin.math.ceil
import kotlin.math.min

internal data class GridLayoutInfo(
    val appsPerRow: Int,
    val panelColumns: Int,
    val rows: Int,
    val panelWidth: Float,
)

internal fun gridLayoutInfo(appCount: Int, appsPerRow: Int, cellWidth: Float, gridPadding: Float): GridLayoutInfo {
    val m = appsPerRow
    val panelColumns = if (appCount in 1 until m) appCount else m
    val rows = if (appCount == 0) 1 else ceil(appCount / m.toFloat()).toInt()
    val panelWidth = panelColumns * cellWidth + gridPadding * 2
    return GridLayoutInfo(m, panelColumns, rows, panelWidth)
}

internal fun visualColumn(index: Int, m: Int, appCount: Int, side: PanelSide): Int {
    val colInRow = index % m
    val row = index / m
    val appsInRow = min(m, appCount - row * m)
    return when (side) {
        PanelSide.RIGHT -> when {
            appCount < m -> appCount - 1 - colInRow
            appsInRow == m -> m - 1 - colInRow
            else -> m - appsInRow + colInRow
        }
        PanelSide.LEFT -> colInRow
    }
}
