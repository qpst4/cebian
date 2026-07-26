package com.slideindex.app.overlay.layout

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.util.coerceSafe
import kotlin.math.min

object TaskSwitcherLayoutEngine {
    fun compute(
        host: TaskSwitcherLayoutHost,
        rows: List<TaskSwitcherRowEntry>,
        scrollOffset: Float,
        anchorLocalY: Float,
    ): Pair<TaskSwitcherPanelLayout, Float> {
        val rowHeight = host.dp(42f)
        val footerHeight = host.dp(36f)
        val panelWidth = host.dp(226f)
        val verticalMargin = host.dp(32f)
        val isEmpty = rows.isEmpty()
        val contentHeight = if (isEmpty) {
            (host.dp(52f) - footerHeight).coerceAtLeast(0f)
        } else {
            rows.size * rowHeight
        }
        val maxListHeight = (host.viewHeight() - verticalMargin - footerHeight).coerceAtLeast(rowHeight * 2f)
        val visibleListHeight = if (isEmpty) contentHeight else min(contentHeight, maxListHeight)
        val maxScrollOffset = (contentHeight - visibleListHeight).coerceAtLeast(0f)
        val coercedOffset = scrollOffset.coerceIn(0f, maxScrollOffset)
        val panelHeight = visibleListHeight + footerHeight
        val trigger = host.activeTriggerZoneRect()
        val anchorY = anchorLocalY.coerceIn(trigger.top, trigger.bottom)
        val gap = host.dp(10f)
        val left = when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> trigger.right + gap
            PanelSide.RIGHT -> trigger.left - gap - panelWidth
        }
        val top = if (host.side() == PanelSide.TOP) {
            (trigger.bottom + gap).coerceSafe(host.dp(16f), (host.viewHeight() - panelHeight - host.dp(16f)).coerceAtLeast(host.dp(16f)))
        } else {
            anchorY - min(rowHeight, visibleListHeight.coerceAtLeast(rowHeight)) / 2f
        }.let { value ->
            value.coerceSafe(host.dp(16f), (host.viewHeight() - panelHeight - host.dp(16f)).coerceAtLeast(host.dp(16f)))
        }
        val panelRect = RectF(left, top, left + panelWidth, top + panelHeight)
        val listRect = RectF(panelRect.left, panelRect.top, panelRect.right, panelRect.top + visibleListHeight)
        val closeAllRect = RectF(
            panelRect.left,
            panelRect.bottom - footerHeight,
            panelRect.right,
            panelRect.bottom,
        )
        val rowLayouts = rows.mapIndexed { index, entry ->
            val rowTop = listRect.top + index * rowHeight - coercedOffset
            val rowRect = RectF(panelRect.left, rowTop, panelRect.right, rowTop + rowHeight)
            val closeRect = closeHitRect(host, rowRect)
            val freeWindowRect = handleColumnRect(host, rowRect)
            TaskSwitcherRowLayout(entry, rowRect, closeRect, freeWindowRect)
        }
        val layout = TaskSwitcherPanelLayout(
            panelRect = panelRect,
            listRect = listRect,
            rows = rowLayouts,
            closeAllRect = closeAllRect,
            scrollOffset = coercedOffset,
            maxScrollOffset = maxScrollOffset,
        )
        return layout to coercedOffset
    }

    fun closeColumnRect(host: TaskSwitcherLayoutHost, rowRect: RectF): RectF {
        val size = actionSize(host)
        val pad = closeEdgePadding(host)
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> RectF(rowRect.left + pad, rowRect.top, rowRect.left + pad + size, rowRect.bottom)
            PanelSide.RIGHT -> RectF(rowRect.right - pad - size, rowRect.top, rowRect.right - pad, rowRect.bottom)
        }
    }

    fun handleColumnRect(host: TaskSwitcherLayoutHost, rowRect: RectF): RectF {
        val size = actionSize(host)
        val centerX = gripCenterX(host, rowRect)
        return RectF(centerX - size / 2f, rowRect.top, centerX + size / 2f, rowRect.bottom)
    }

    fun closeIconRect(host: TaskSwitcherLayoutHost, rowRect: RectF): RectF {
        val size = actionSize(host)
        val column = closeColumnRect(host, rowRect)
        val cy = rowRect.centerY()
        return RectF(column.left, cy - size / 2f, column.right, cy + size / 2f)
    }

    fun closeHitRect(host: TaskSwitcherLayoutHost, rowRect: RectF): RectF {
        val column = closeColumnRect(host, rowRect)
        val slop = hitSlop(host)
        val gapReach = host.dp(10f)
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> RectF(column.left - slop - gapReach, column.top, column.right + slop, column.bottom)
            PanelSide.RIGHT -> RectF(column.left - slop, column.top, column.right + slop + gapReach, column.bottom)
        }
    }

    fun gripX(host: TaskSwitcherLayoutHost, rowRect: RectF): Float {
        val inset = actionIconInset(host)
        val radius = gripDotRadius(host)
        val gapX = gripGapX(host)
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> rowRect.right - inset - gapX - radius
            PanelSide.RIGHT -> rowRect.left + inset + radius
        }
    }

    fun gripCenterX(host: TaskSwitcherLayoutHost, rowRect: RectF): Float =
        gripX(host, rowRect) + gripGapX(host) / 2f

    fun iconLeft(host: TaskSwitcherLayoutHost, row: TaskSwitcherRowLayout): Float {
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> closeColumnRect(host, row.rowRect).right + host.dp(4f)
            PanelSide.RIGHT -> handleColumnRect(host, row.rowRect).right + host.dp(4f)
        }
    }

    fun labelMaxWidth(host: TaskSwitcherLayoutHost, row: TaskSwitcherRowLayout, labelX: Float): Float {
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> handleColumnRect(host, row.rowRect).left - labelX - host.dp(8f)
            PanelSide.RIGHT -> closeColumnRect(host, row.rowRect).left - labelX - host.dp(6f)
        }.coerceAtLeast(host.dp(24f))
    }

    private fun actionIconInset(host: TaskSwitcherLayoutHost): Float = host.dp(9.5f)

    private fun actionSize(host: TaskSwitcherLayoutHost): Float = host.dp(30f)

    private fun closeEdgePadding(host: TaskSwitcherLayoutHost): Float = host.dp(5.5f)

    private fun hitSlop(host: TaskSwitcherLayoutHost): Float = host.dp(2f)

    private fun gripDotRadius(host: TaskSwitcherLayoutHost): Float = host.dp(1.65f)

    private fun gripGapX(host: TaskSwitcherLayoutHost): Float = host.dp(3f)

    fun gripGapY(host: TaskSwitcherLayoutHost): Float = host.dp(3.6f)
}
