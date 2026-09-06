package com.slideindex.app.widget

object WidgetPanelGridLogic {
    fun computeContentRowCount(page: WidgetPanelPage): Int {
        val maxOccupiedY = page.items.maxOfOrNull { it.y + it.spanY } ?: 0
        return maxOf(page.visibleRowCount, maxOccupiedY)
    }

    fun computeEditRowCount(page: WidgetPanelPage, bufferRows: Int = 5): Int {
        return computeContentRowCount(page) + bufferRows
    }

    fun isAreaFree(
        page: WidgetPanelPage,
        x: Int,
        y: Int,
        spanX: Int,
        spanY: Int,
        ignoreWidgetId: Int? = null,
        maxRowLimit: Int? = null,
    ): Boolean {
        if (x < 0 || y < 0 || x + spanX > page.columnCount) {
            return false
        }
        if (maxRowLimit != null && y + spanY > maxRowLimit) {
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
        if (spanX > page.columnCount) return null
        val contentBottom = page.items.maxOfOrNull { it.y + it.spanY } ?: 0
        for (y in 0 until contentBottom) {
            for (x in 0..(page.columnCount - spanX)) {
                if (isAreaFree(page, x, y, spanX, spanY)) return x to y
            }
        }
        return 0 to contentBottom
    }

    fun removeItem(page: WidgetPanelPage, appWidgetId: Int): WidgetPanelPage {
        val without = page.items.filterNot { it.appWidgetId == appWidgetId }
        val updated = page.copy(items = without)
        return updated.copy(rowCount = computeContentRowCount(updated))
    }

    fun upsertItem(page: WidgetPanelPage, item: WidgetPanelItem): WidgetPanelPage {
        val without = page.items.filterNot { it.appWidgetId == item.appWidgetId }
        val updated = page.copy(items = without + item)
        return updated.copy(rowCount = computeContentRowCount(updated))
    }

    fun fitItemToGrid(page: WidgetPanelPage, item: WidgetPanelItem): WidgetPanelItem {
        val spanX = item.spanX.coerceIn(1, page.columnCount)
        val spanY = item.spanY.coerceAtLeast(1)
        val x = item.x.coerceIn(0, (page.columnCount - spanX).coerceAtLeast(0))
        val y = item.y.coerceAtLeast(0)
        return item.copy(x = x, y = y, spanX = spanX, spanY = spanY)
    }

    fun fitPageToGrid(page: WidgetPanelPage): WidgetPanelPage {
        if (page.items.isEmpty()) return page.copy(rowCount = computeContentRowCount(page))
        val updated = page.copy(items = page.items.map { fitItemToGrid(page, it) })
        return updated.copy(rowCount = computeContentRowCount(updated))
    }

    fun moveItemWithAutoSwapOrShift(
        page: WidgetPanelPage,
        targetItem: WidgetPanelItem,
        targetX: Int,
        targetY: Int,
    ): WidgetPanelPage {
        val clampedX = targetX.coerceIn(0, (page.columnCount - targetItem.spanX).coerceAtLeast(0))
        val clampedY = targetY.coerceAtLeast(0)

        if (isAreaFree(page, clampedX, clampedY, targetItem.spanX, targetItem.spanY, targetItem.appWidgetId)) {
            return upsertItem(page, targetItem.copy(x = clampedX, y = clampedY))
        }

        // Find overlapping items
        val overlapping = page.items.filter {
            it.appWidgetId != targetItem.appWidgetId &&
            rectsOverlap(clampedX, clampedY, targetItem.spanX, targetItem.spanY, it.x, it.y, it.spanX, it.spanY)
        }

        // 1-to-1 exact swap if single overlap matches span
        if (overlapping.size == 1) {
            val other = overlapping.first()
            if (other.spanX == targetItem.spanX && other.spanY == targetItem.spanY) {
                val newTarget = targetItem.copy(x = clampedX, y = clampedY)
                val newOther = other.copy(x = targetItem.x, y = targetItem.y)
                val others = page.items.filterNot { it.appWidgetId == targetItem.appWidgetId || it.appWidgetId == other.appWidgetId }
                return page.copy(items = others + newTarget + newOther)
            }
        }

        // General push / shift to next free slot
        val newTarget = targetItem.copy(x = clampedX, y = clampedY)
        var tempPage = page.copy(items = page.items.filterNot { it.appWidgetId == targetItem.appWidgetId } + newTarget)
        val remainingItems = tempPage.items.toMutableList()
        for (overlap in overlapping) {
            val pageWithoutOverlap = tempPage.copy(items = remainingItems.filterNot { it.appWidgetId == overlap.appWidgetId })
            val freeSlot = findFirstFreeSlot(pageWithoutOverlap, overlap.spanX, overlap.spanY)
            if (freeSlot != null) {
                val shifted = overlap.copy(x = freeSlot.first, y = freeSlot.second)
                remainingItems.removeAll { it.appWidgetId == overlap.appWidgetId }
                remainingItems.add(shifted)
                tempPage = tempPage.copy(items = remainingItems)
            } else {
                return page
            }
        }
        return tempPage
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
