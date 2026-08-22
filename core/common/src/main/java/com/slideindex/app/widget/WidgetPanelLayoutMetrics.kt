package com.slideindex.app.widget

import kotlin.math.roundToInt

object WidgetPanelLayoutMetrics {
    data class Result(
        val panelWidthPx: Int,
        /** Distance between adjacent grid dots; one span equals this width. */
        val gridStepPx: Int,
        val gridPadPx: Int,
        val viewportHeightPx: Int,
    )

    fun compute(
        screenWidthPx: Int,
        page: WidgetPanelPage,
        density: Float,
        panelPaddingDp: Float = 12f,
        panelInnerPaddingDp: Float = 4f,
        horizontalInsetDp: Float = 16f,
    ): Result {
        val horizontalInsetPx = (horizontalInsetDp * density).roundToInt()
        val panelPaddingPx = (panelPaddingDp * density).roundToInt()
        val gridPadPx = (panelInnerPaddingDp * density).roundToInt()
        val columnCount = page.columnCount.coerceAtLeast(1)
        val visibleRows = page.visibleRowCount.coerceAtLeast(1)

        val maxPanelWidthPx = (screenWidthPx - horizontalInsetPx * 2).coerceAtLeast(1)
        val desiredCellWidthPx = if (page.cellWidthDp > 0) (page.cellWidthDp * density).roundToInt() else 0

        val (panelWidthPx, gridStepPx) = if (desiredCellWidthPx > 0) {
            val step = desiredCellWidthPx
            val panelW = step * columnCount + panelPaddingPx * 2 + gridPadPx * 2
            panelW to step
        } else {
            val innerForCellsPx = (maxPanelWidthPx - panelPaddingPx * 2 - gridPadPx * 2).coerceAtLeast(columnCount)
            val step = WidgetGridMetrics.computeGridStepPx(innerForCellsPx, columnCount)
            maxPanelWidthPx to step
        }

        val viewportInnerPx = visibleRows * gridStepPx
        val viewportHeightPx = viewportInnerPx + gridPadPx * 2

        return Result(
            panelWidthPx = panelWidthPx,
            gridStepPx = gridStepPx,
            gridPadPx = gridPadPx,
            viewportHeightPx = viewportHeightPx,
        )
    }
}
