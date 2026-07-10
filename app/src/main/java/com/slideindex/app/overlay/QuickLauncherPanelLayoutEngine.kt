package com.slideindex.app.overlay

import android.graphics.RectF
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.coerceSafe

internal object QuickLauncherPanelLayoutEngine {
    const val MAX_ROWS = 6

    fun gridLayoutInfo(
        settings: AppSettings,
        cellWidth: Float,
        gridPadding: Float,
    ): GridLayoutInfo {
        val columns = settings.quickLauncherColumnsPerPage.coerceIn(2, 5)
        val rows = settings.quickLauncherRowsPerPage.coerceIn(2, MAX_ROWS)
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
        host: QuickLauncherOverlayController.Host,
        panelWidth: Float,
        contentHeight: Float,
        anchorLocalY: Float,
    ): RectF {
        val trigger = host.activeTriggerZoneRect()
        val anchorY = anchorLocalY.coerceIn(trigger.top, trigger.bottom)
        var top = anchorY - contentHeight / 2f
        top = top.coerceSafe(host.dp(16f), host.viewHeight() - contentHeight - host.dp(16f))
        val gap = host.dp(8f)
        val left = when (host.side()) {
            PanelSide.LEFT -> trigger.right + gap
            PanelSide.RIGHT -> trigger.left - gap - panelWidth
        }
        return RectF(left, top, left + panelWidth, top + contentHeight)
    }

    fun offsetForToolbar(
        host: QuickLauncherOverlayController.Host,
        panelRect: RectF,
        reserveWidth: Float,
    ): RectF {
        if (panelRect.isEmpty) return panelRect
        val margin = host.dp(16f)
        var left = panelRect.left
        var right = panelRect.right
        return when (host.side()) {
            PanelSide.LEFT -> {
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
        host: QuickLauncherOverlayController.Host,
        settings: AppSettings,
        cellWidth: Float,
        cellHeight: Float,
        gridPadding: Float,
        headerHeight: Float,
        anchorLocalY: Float,
        toolbarReserveWidth: Float,
    ): RectF {
        val layout = gridLayoutInfo(settings, cellWidth, gridPadding)
        val height = contentHeight(layout.rows, cellHeight, gridPadding, headerHeight)
        val base = anchoredPanelRect(host, layout.panelWidth, height, anchorLocalY)
        return offsetForToolbar(host, base, toolbarReserveWidth)
    }
}
