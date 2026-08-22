package com.slideindex.app.launcher

/**
 * Shared grid reorder / preview logic for the settings editor and overlay sidebar.
 *
 * Data model: a flat [items] list laid out left-to-right, top-to-bottom with [pageSize] slots
 * per page (columns * rows). Empty grid slots only exist after the last item on a partial page.
 */
object QuickLauncherGridLogic {

    fun pageSize(columns: Int, rows: Int): Int = columns.coerceAtLeast(1) * rows.coerceAtLeast(1)

    fun pageCount(itemCount: Int, pageSize: Int): Int {
        if (pageSize <= 0) return 1
        return maxOf(1, (itemCount + pageSize - 1) / pageSize)
    }

    fun pageStart(pageIndex: Int, pageSize: Int): Int = pageIndex.coerceAtLeast(0) * pageSize

    fun <T> List<T>.moveIndex(from: Int, to: Int): List<T> {
        if (from == to || from !in indices || to !in 0..size) return this
        val mutable = toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to.coerceIn(0, mutable.size), item)
        return mutable
    }

    /** Visual grid slot under the pointer (may exceed [itemCount] - 1 on partial pages). */
    fun dragSlotGlobal(pageStart: Int, localSlot: Int, pageSize: Int): Int {
        return (pageStart + localSlot).coerceIn(0, pageStart + pageSize - 1)
    }

    /** List insert index used by [moveIndex] (always in 0..itemCount). */
    fun dragInsertIndex(dragSlotGlobal: Int, itemCount: Int): Int {
        return dragSlotGlobal.coerceIn(0, itemCount)
    }

    fun localSlotAt(
        x: Float,
        y: Float,
        columns: Int,
        rows: Int,
        pageSize: Int,
        cellWidthPx: Float,
        cellHeightPx: Float,
        gapPx: Float,
    ): Int {
        if (cellWidthPx <= 0f || cellHeightPx <= 0f || columns <= 0 || rows <= 0) return 0
        val stepX = cellWidthPx + gapPx
        val stepY = cellHeightPx + gapPx
        var col = columns - 1
        for (c in 0 until columns) {
            val left = c * stepX
            if (x < left + cellWidthPx) {
                col = c
                break
            }
        }
        var row = rows - 1
        for (r in 0 until rows) {
            val top = r * stepY
            if (y < top + cellHeightPx) {
                row = r
                break
            }
        }
        return (row * columns + col).coerceIn(0, pageSize - 1)
    }

    /**
     * Preview mapping for one page slice of the flat grid.
     *
     * @param mappingSize usually [pageStart] + [pageSize] so empty trailing slots resolve to null.
     * @return original item index shown at each global slot, or null for a hole / empty cell.
     */
    fun displayMapping(
        itemCount: Int,
        dragFrom: Int,
        dragSlotGlobal: Int,
        mappingSize: Int,
        mergeTargetGlobal: Int = -1,
    ): List<Int?> {
        if (mappingSize <= 0) return emptyList()
        if (itemCount <= 0) return List(mappingSize) { null }
        if (dragFrom < 0) {
            return List(mappingSize) { slot -> if (slot < itemCount) slot else null }
        }

        if (mergeTargetGlobal >= 0 && mergeTargetGlobal != dragFrom && mergeTargetGlobal < itemCount) {
            return List(mappingSize) { slot ->
                when {
                    slot == dragFrom -> null
                    slot < itemCount -> slot
                    else -> null
                }
            }
        }

        val insertIndex = dragInsertIndex(dragSlotGlobal, itemCount)
        val indices = (0 until itemCount).toList()
        val reordered = if (dragFrom == insertIndex) {
            indices
        } else {
            indices.moveIndex(dragFrom, insertIndex)
        }

        return List(mappingSize) { slot ->
            when {
                dragFrom == insertIndex && slot == dragFrom -> null
                dragFrom != insertIndex && slot == dragSlotGlobal -> null
                slot >= itemCount -> null
                else -> reordered[slot]
            }
        }
    }

    fun displayMappingForPage(
        itemCount: Int,
        dragFrom: Int,
        dragSlotGlobal: Int,
        pageStart: Int,
        pageSize: Int,
        mergeTargetGlobal: Int = -1,
    ): List<Int?> {
        val mappingSize = pageStart + pageSize
        return displayMapping(itemCount, dragFrom, dragSlotGlobal, mappingSize, mergeTargetGlobal)
            .drop(pageStart)
    }

    fun pagesCacheKey(panelId: String, itemCount: Int, itemHash: Int, pageSize: Int, columns: Int, rows: Int): Int {
        var key = panelId.hashCode() * 31 + itemCount
        key = key * 31 + itemHash
        key = key * 31 + pageSize
        key = key * 31 + columns
        key = key * 31 + rows
        return key
    }
}
