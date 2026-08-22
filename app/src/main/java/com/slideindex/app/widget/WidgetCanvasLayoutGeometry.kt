package com.slideindex.app.widget

import android.content.Context
import com.slideindex.app.widget.WidgetCanvasLayout.BindKey

internal object WidgetCanvasLayoutGeometry {
    fun bindKeyFor(page: WidgetPanelPage): BindKey = BindKey(
        pageId = page.id,
        itemsSignature = itemsSignature(page),
        columnCount = page.columnCount,
        rowCount = page.rowCount,
    )

    fun itemsSignature(page: WidgetPanelPage): String =
        page.items.joinToString("|") {
            "${it.appWidgetId}:${it.x},${it.y},${it.spanX},${it.spanY}"
        }

    fun syncItemsFromPage(layout: WidgetCanvasLayout, page: WidgetPanelPage) {
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i) as? WidgetCardContainer ?: continue
            val updated = page.items.find { it.appWidgetId == child.item.appWidgetId } ?: continue
            if (child.item == updated) continue
            child.syncItem(updated)
            if (!child.isPreviewingResize()) {
                child.post { child.refreshWidgetLayout(force = true) }
            }
        }
    }

    fun applyPageGeometry(layout: WidgetCanvasLayout, page: WidgetPanelPage) {
        layout.canvasPage = page
        layout.pageColumnCount = page.columnCount
        layout.pageRowCount = page.rowCount
        syncItemsFromPage(layout, page)
        layout.requestLayout()
        layout.invalidate()
        layout.post { layout.refreshAllWidgetLayouts() }
    }

    fun applyPageItems(layout: WidgetCanvasLayout, page: WidgetPanelPage, hostContext: Context) {
        layout.canvasPage = page
        var structureChanged = false
        for (i in layout.childCount - 1 downTo 0) {
            val child = layout.getChildAt(i) as? WidgetCardContainer ?: continue
            if (page.items.none { it.appWidgetId == child.item.appWidgetId }) {
                layout.removeViewAt(i)
                structureChanged = true
            }
        }
        syncItemsFromPage(layout, page)
        for (item in page.items) {
            if (layout.findChildByWidgetId(item.appWidgetId) == null) {
                layout.addWidgetCard(hostContext, item)
                structureChanged = true
            }
        }
        layout.requestLayout()
        layout.invalidate()
        if (structureChanged) {
            layout.post { layout.refreshAllWidgetLayouts() }
        }
    }

    fun resolvePageForBind(layout: WidgetCanvasLayout, incoming: WidgetPanelPage): WidgetPanelPage {
        val local = layout.canvasPage ?: return incoming
        if (local.id != incoming.id) return incoming
        if (itemsSignature(local) == itemsSignature(incoming)) return incoming

        val incomingIds = incoming.items.map { it.appWidgetId }.toSet()
        val localIds = local.items.map { it.appWidgetId }.toSet()
        if (incomingIds != localIds) {
            return incoming
        }
        return local
    }

    fun bindIfNeeded(layout: WidgetCanvasLayout, page: WidgetPanelPage, hostContext: Context) {
        layout.canvasHostContext = hostContext
        if (layout.canvasInteractionActive || layout.draggingChild != null || layout.anyChildPreviewingResize()) return
        val resolvedPage = resolvePageForBind(layout, page)
        val key = bindKeyFor(resolvedPage)
        val previous = layout.lastBindKey
        layout.lastBindKey = key
        if (previous == null || previous.pageId != key.pageId) {
            layout.bind(resolvedPage, hostContext)
            return
        }
        if (previous.itemsSignature != key.itemsSignature) {
            applyPageItems(layout, resolvedPage, hostContext)
            return
        }
        if (previous != key) {
            applyPageGeometry(layout, resolvedPage)
        }
    }

    fun updateHoverCell(layout: WidgetCanvasLayout, x: Float, y: Float) {
        val step = layout.currentGridStepPx
        if (step <= 0) return
        val item = layout.draggingItem ?: return
        val topLeftX = x - layout.dragTouchOffsetX - layout.paddingLeft
        val topLeftY = y - layout.dragTouchOffsetY - layout.paddingTop
        val newHoverX = kotlin.math.round(topLeftX / step).toInt().coerceIn(0, layout.pageColumnCount - item.spanX)
        val newHoverY = kotlin.math.round(topLeftY / step).toInt().coerceIn(0, layout.pageRowCount - item.spanY)
        if (layout.hoverCellX != newHoverX || layout.hoverCellY != newHoverY) {
            layout.hoverCellX = newHoverX
            layout.hoverCellY = newHoverY
            layout.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}
