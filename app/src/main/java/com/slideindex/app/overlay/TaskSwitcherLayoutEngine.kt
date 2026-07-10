package com.slideindex.app.overlay

import android.graphics.RectF
import com.slideindex.app.util.RecentAppEntry
import com.slideindex.app.util.coerceSafe
import kotlin.math.min

internal object TaskSwitcherLayoutEngine {
    fun compute(
        host: TaskSwitcherOverlayController.Host,
        recentApps: List<RecentAppEntry>,
        scrollOffset: Float,
        anchorLocalY: Float,
    ): Pair<TaskSwitcherPanelLayout, Float> {
        val rowHeight = host.dp(42f)
        val footerHeight = host.dp(36f)
        val panelWidth = host.dp(226f)
        val verticalMargin = host.dp(32f)
        val isEmpty = recentApps.isEmpty()
        val contentHeight = if (isEmpty) {
            (host.dp(52f) - footerHeight).coerceAtLeast(0f)
        } else {
            recentApps.size * rowHeight
        }
        val maxListHeight = (host.viewHeight() - verticalMargin - footerHeight).coerceAtLeast(rowHeight * 2f)
        val visibleListHeight = if (isEmpty) contentHeight else min(contentHeight, maxListHeight)
        val maxScrollOffset = (contentHeight - visibleListHeight).coerceAtLeast(0f)
        val coercedOffset = scrollOffset.coerceIn(0f, maxScrollOffset)
        val panelHeight = visibleListHeight + footerHeight
        val trigger = host.activeTriggerZoneRect()
        val anchorY = anchorLocalY.coerceIn(trigger.top, trigger.bottom)
        var top = anchorY - min(rowHeight, visibleListHeight.coerceAtLeast(rowHeight)) / 2f
        top = top.coerceSafe(host.dp(16f), (host.viewHeight() - panelHeight - host.dp(16f)).coerceAtLeast(host.dp(16f)))
        val gap = host.dp(10f)
        val left = when (host.side()) {
            PanelSide.LEFT -> trigger.right + gap
            PanelSide.RIGHT -> trigger.left - gap - panelWidth
        }
        val panelRect = RectF(left, top, left + panelWidth, top + panelHeight)
        val listRect = RectF(panelRect.left, panelRect.top, panelRect.right, panelRect.top + visibleListHeight)
        val closeAllRect = RectF(
            panelRect.left,
            panelRect.bottom - footerHeight,
            panelRect.right,
            panelRect.bottom,
        )
        val rows = recentApps.mapIndexed { index, entry ->
            val rowTop = listRect.top + index * rowHeight - coercedOffset
            val rowRect = RectF(panelRect.left, rowTop, panelRect.right, rowTop + rowHeight)
            val closeRect = closeHitRect(host, rowRect)
            val freeWindowRect = handleColumnRect(host, rowRect)
            TaskSwitcherRowLayout(entry, rowRect, closeRect, freeWindowRect)
        }
        val layout = TaskSwitcherPanelLayout(
            panelRect = panelRect,
            listRect = listRect,
            rows = rows,
            closeAllRect = closeAllRect,
            scrollOffset = coercedOffset,
            maxScrollOffset = maxScrollOffset,
        )
        return layout to coercedOffset
    }

    fun closeColumnRect(host: TaskSwitcherOverlayController.Host, rowRect: RectF): RectF {
        val size = actionSize(host)
        val pad = closeEdgePadding(host)
        return when (host.side()) {
            PanelSide.LEFT -> RectF(rowRect.left + pad, rowRect.top, rowRect.left + pad + size, rowRect.bottom)
            PanelSide.RIGHT -> RectF(rowRect.right - pad - size, rowRect.top, rowRect.right - pad, rowRect.bottom)
        }
    }

    fun handleColumnRect(host: TaskSwitcherOverlayController.Host, rowRect: RectF): RectF {
        val size = actionSize(host)
        val centerX = gripCenterX(host, rowRect)
        return RectF(centerX - size / 2f, rowRect.top, centerX + size / 2f, rowRect.bottom)
    }

    fun closeIconRect(host: TaskSwitcherOverlayController.Host, rowRect: RectF): RectF {
        val size = actionSize(host)
        val column = closeColumnRect(host, rowRect)
        val cy = rowRect.centerY()
        return RectF(column.left, cy - size / 2f, column.right, cy + size / 2f)
    }

    fun closeHitRect(host: TaskSwitcherOverlayController.Host, rowRect: RectF): RectF {
        val column = closeColumnRect(host, rowRect)
        val slop = hitSlop(host)
        val gapReach = host.dp(10f)
        return when (host.side()) {
            PanelSide.LEFT -> RectF(column.left - slop - gapReach, column.top, column.right + slop, column.bottom)
            PanelSide.RIGHT -> RectF(column.left - slop, column.top, column.right + slop + gapReach, column.bottom)
        }
    }

    fun gripX(host: TaskSwitcherOverlayController.Host, rowRect: RectF): Float {
        val inset = actionIconInset(host)
        val radius = gripDotRadius(host)
        val gapX = gripGapX(host)
        return when (host.side()) {
            PanelSide.LEFT -> rowRect.right - inset - gapX - radius
            PanelSide.RIGHT -> rowRect.left + inset + radius
        }
    }

    fun gripCenterX(host: TaskSwitcherOverlayController.Host, rowRect: RectF): Float =
        gripX(host, rowRect) + gripGapX(host) / 2f

    fun iconLeft(host: TaskSwitcherOverlayController.Host, row: TaskSwitcherRowLayout): Float {
        return when (host.side()) {
            PanelSide.LEFT -> closeColumnRect(host, row.rowRect).right + host.dp(4f)
            PanelSide.RIGHT -> handleColumnRect(host, row.rowRect).right + host.dp(4f)
        }
    }

    fun labelMaxWidth(host: TaskSwitcherOverlayController.Host, row: TaskSwitcherRowLayout, labelX: Float): Float {
        return when (host.side()) {
            PanelSide.LEFT -> handleColumnRect(host, row.rowRect).left - labelX - host.dp(8f)
            PanelSide.RIGHT -> closeColumnRect(host, row.rowRect).left - labelX - host.dp(6f)
        }.coerceAtLeast(host.dp(24f))
    }

    private fun actionIconInset(host: TaskSwitcherOverlayController.Host): Float = host.dp(9.5f)

    private fun actionSize(host: TaskSwitcherOverlayController.Host): Float = host.dp(30f)

    private fun closeEdgePadding(host: TaskSwitcherOverlayController.Host): Float = host.dp(5.5f)

    private fun hitSlop(host: TaskSwitcherOverlayController.Host): Float = host.dp(2f)

    private fun gripDotRadius(host: TaskSwitcherOverlayController.Host): Float = host.dp(1.65f)

    private fun gripGapX(host: TaskSwitcherOverlayController.Host): Float = host.dp(3f)

    fun gripGapY(host: TaskSwitcherOverlayController.Host): Float = host.dp(3.6f)
}
