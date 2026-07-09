package com.slideindex.app.widget

object WidgetPanelGridLogic {
    fun isAreaFree(
        page: WidgetPanelPage,
        x: Int,
        y: Int,
        spanX: Int,
        spanY: Int,
        ignoreWidgetId: Int? = null,
    ): Boolean {
        if (x < 0 || y < 0 || x + spanX > page.columnCount || y + spanY > page.rowCount) {
            return false
        }
        for (item in page.items) {
            if (item.appWidgetId == ignoreWidgetId) continue
            if (rectsOverlap(x, y, spanX, spanY, item.x, item.y, item.spanX, item.spanY)) {
                return false
            }
        }
        return true
    }

    fun findFirstFreeSlot(page: WidgetPanelPage, spanX: Int, spanY: Int): Pair<Int, Int>? {
        for (y in 0 until page.rowCount) {
            for (x in 0 until page.columnCount) {
                if (isAreaFree(page, x, y, spanX, spanY)) return x to y
            }
        }
        return null
    }

    fun removeItem(page: WidgetPanelPage, appWidgetId: Int): WidgetPanelPage =
        page.copy(items = page.items.filterNot { it.appWidgetId == appWidgetId })

    fun upsertItem(page: WidgetPanelPage, item: WidgetPanelItem): WidgetPanelPage {
        val without = page.items.filterNot { it.appWidgetId == item.appWidgetId }
        return page.copy(items = without + item)
    }

    fun fitItemToGrid(page: WidgetPanelPage, item: WidgetPanelItem): WidgetPanelItem {
        val spanX = item.spanX.coerceIn(1, page.columnCount)
        val spanY = item.spanY.coerceIn(1, page.rowCount)
        val x = item.x.coerceIn(0, (page.columnCount - spanX).coerceAtLeast(0))
        val y = item.y.coerceIn(0, (page.rowCount - spanY).coerceAtLeast(0))
        return item.copy(x = x, y = y, spanX = spanX, spanY = spanY)
    }

    fun fitPageToGrid(page: WidgetPanelPage): WidgetPanelPage {
        if (page.items.isEmpty()) return page
        return page.copy(items = page.items.map { fitItemToGrid(page, it) })
    }

    private fun rectsOverlap(
        x1: Int,
        y1: Int,
        w1: Int,
        h1: Int,
        x2: Int,
        y2: Int,
        w2: Int,
        h2: Int,
    ): Boolean {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2
    }
}
