package com.slideindex.app.overlay.layout

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.util.coerceSafe

object QuickLauncherPanelLayoutEngine {
    const val MAX_ROWS = 6

    fun gridLayoutInfo(
        columnsPerPage: Int,
        rowsPerPage: Int,
        cellWidth: Float,
        gridPadding: Float,
    ): GridLayoutInfo {
        val columns = columnsPerPage.coerceIn(2, 5)
        val rows = rowsPerPage.coerceIn(2, MAX_ROWS)
        val panelWidth = columns * cellWidth + gridPadding * 2
        return GridLayoutInfo(columns, columns, rows, panelWidth)
    }

    fun contentHeight(
        rows: Int,
        cellHeight: Float,
        gridPadding: Float,
        headerHeight: Float,
    ): Float = rows * cellHeight + gridPadding * 2 + headerHeight

    fun anchoredPanelRect(
        host: OverlayPanelLayoutHost,
        panelWidth: Float,
        contentHeight: Float,
        anchorLocalY: Float,
    ): RectF {
        val trigger = host.activeTriggerZoneRect()
        val anchorY = anchorLocalY.coerceIn(trigger.top, trigger.bottom)
        val gap = host.dp(8f)
        val left = when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> trigger.right + gap
            PanelSide.RIGHT -> trigger.left - gap - panelWidth
        }
        val top = if (host.side() == PanelSide.TOP) {
            (trigger.bottom + gap).coerceSafe(host.dp(16f), host.viewHeight() - contentHeight - host.dp(16f))
        } else {
            (anchorY - contentHeight / 2f).coerceSafe(host.dp(16f), host.viewHeight() - contentHeight - host.dp(16f))
        }
        return RectF(left, top, left + panelWidth, top + contentHeight)
    }

    fun offsetForToolbar(
        host: OverlayPanelLayoutHost,
        panelRect: RectF,
        reserveWidth: Float,
    ): RectF {
        if (panelRect.isEmpty) return panelRect
        val margin = host.dp(16f)
        var left = panelRect.left
        var right = panelRect.right
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> {
                if (left < margin) {
                    val delta = margin - left
                    left += delta
                    right += delta
                }
                if (right > host.viewWidth() - margin) {
                    val delta = right - (host.viewWidth() - margin)
                    left -= delta
                    right = host.viewWidth() - margin
                    left = left.coerceAtLeast(margin)
                }
                RectF(left, panelRect.top, right, panelRect.bottom)
            }
            PanelSide.RIGHT -> {
                val combinedLeft = left - reserveWidth
                if (combinedLeft < margin) {
                    val delta = margin - combinedLeft
                    left += delta
                    right += delta
                }
                if (right > host.viewWidth() - margin) {
                    val delta = right - (host.viewWidth() - margin)
                    left -= delta
                    right = host.viewWidth() - margin
                    left = left.coerceAtLeast(margin)
                }
                RectF(left, panelRect.top, right, panelRect.bottom)
            }
        }
    }

    fun panelRect(
        host: OverlayPanelLayoutHost,
        columnsPerPage: Int,
        rowsPerPage: Int,
        cellWidth: Float,
        cellHeight: Float,
        gridPadding: Float,
        headerHeight: Float,
        anchorLocalY: Float,
        toolbarReserveWidth: Float,
    ): RectF {
        val layout = gridLayoutInfo(columnsPerPage, rowsPerPage, cellWidth, gridPadding)
        val height = contentHeight(layout.rows, cellHeight, gridPadding, headerHeight)
        val base = anchoredPanelRect(host, layout.panelWidth, height, anchorLocalY)
        return offsetForToolbar(host, base, toolbarReserveWidth)
    }
}
