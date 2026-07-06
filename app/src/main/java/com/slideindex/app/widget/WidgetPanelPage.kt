package com.slideindex.app.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import kotlin.math.roundToInt

data class WidgetPanelItem(
    val appWidgetId: Int,
    val x: Int,
    val y: Int,
    val spanX: Int,
    val spanY: Int,
    val label: String = "",
)

data class WidgetPanelPage(
    val id: Long = 1L,
    val name: String = "",
    val columnCount: Int = 4,
    val rowCount: Int = 26,
    val visibleRowCount: Int = 6,
    val cellWidthDp: Int = 62,
    val marginLeftDp: Int = 18,
    val marginTopDp: Int = 100,
    val items: List<WidgetPanelItem> = emptyList(),
    val overlayAlpha: Float = 0.55f,
    val blurEnabled: Boolean = true,
)

object WidgetPanelDefaults {
    val defaultPage: WidgetPanelPage = WidgetPanelPage()

    fun effectivePages(pages: List<WidgetPanelPage>): List<WidgetPanelPage> =
        pages.ifEmpty { listOf(defaultPage) }
}

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

    /** Re-read provider span metadata only for suspicious 1×1 saves; preserves user resize. */
    fun repairAndFitPage(context: Context, page: WidgetPanelPage): WidgetPanelPage {
        if (page.items.isEmpty()) return page
        var placed = emptyList<WidgetPanelItem>()
        val repaired = mutableListOf<WidgetPanelItem>()
        for (item in page.items) {
            val info = WidgetPopupHost.providerInfo(context, item.appWidgetId)
            val candidate = if (info != null) {
                repairUndersizedSpan(page, item, info)
            } else {
                fitItemToGrid(page, item)
            }
            val pageSoFar = page.copy(items = placed)
            val fitted = if (
                isAreaFree(pageSoFar, candidate.x, candidate.y, candidate.spanX, candidate.spanY, item.appWidgetId)
            ) {
                candidate
            } else {
                val slot = findFirstFreeSlot(pageSoFar, candidate.spanX, candidate.spanY)
                if (slot != null) {
                    fitItemToGrid(page, candidate.copy(x = slot.first, y = slot.second))
                } else {
                    fitItemToGrid(page, item)
                }
            }
            repaired += fitted
            placed = repaired.toList()
        }
        return page.copy(items = repaired)
    }

    private fun repairUndersizedSpan(
        page: WidgetPanelPage,
        item: WidgetPanelItem,
        info: AppWidgetProviderInfo,
    ): WidgetPanelItem {
        val (providerSpanX, providerSpanY) = WidgetSpanUtil.spanFromProviderInfo(info)
        val needsRepair = (item.spanX == 1 && providerSpanX > 1) ||
            (item.spanY == 1 && providerSpanY > 1)
        if (!needsRepair) {
            return fitItemToGrid(page, item)
        }
        val spanX = maxOf(item.spanX, providerSpanX).coerceIn(1, page.columnCount)
        val spanY = maxOf(item.spanY, providerSpanY).coerceIn(1, page.rowCount)
        return fitItemToGrid(page, item.copy(spanX = spanX, spanY = spanY))
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
        val innerForCellsPx = (maxPanelWidthPx - panelPaddingPx * 2 - gridPadPx * 2)
            .coerceAtLeast(columnCount)
        val gridStepPx = WidgetSizeHelper.computeGridStepPx(innerForCellsPx, columnCount)

        val viewportInnerPx = visibleRows * gridStepPx
        val viewportHeightPx = viewportInnerPx + gridPadPx * 2

        return Result(
            panelWidthPx = maxPanelWidthPx,
            gridStepPx = gridStepPx,
            gridPadPx = gridPadPx,
            viewportHeightPx = viewportHeightPx,
        )
    }
}

object WidgetSpanUtil {
    private const val CELL_SIZE_DP = 70
    private const val CELL_PADDING_DP = 30

    fun spanFromProviderInfo(info: AppWidgetProviderInfo): Pair<Int, Int> {
        val fromMin = dpToSpan(info.minWidth) to dpToSpan(info.minHeight)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val targetX = info.targetCellWidth
            val targetY = info.targetCellHeight
            val spanX = if (targetX > 0) maxOf(targetX, fromMin.first) else fromMin.first
            val spanY = if (targetY > 0) maxOf(targetY, fromMin.second) else fromMin.second
            return spanX.coerceAtLeast(1) to spanY.coerceAtLeast(1)
        }
        return fromMin
    }

    fun dpToSpan(sizeDp: Int): Int =
        ((sizeDp + CELL_PADDING_DP) / CELL_SIZE_DP).coerceAtLeast(1)
}

object WidgetPanelCodec {
    private const val PAGE_SEP = "\u001F"
    private const val FIELD_SEP = "\u001E"
    private const val ITEM_SEP = "\u001D"

    fun encodePage(page: WidgetPanelPage): String {
        val header = listOf(
            page.id.toString(),
            page.name,
            page.columnCount.toString(),
            page.rowCount.toString(),
            page.overlayAlpha.toString(),
            page.blurEnabled.toString(),
            page.visibleRowCount.toString(),
            page.cellWidthDp.toString(),
            page.marginLeftDp.toString(),
            page.marginTopDp.toString(),
        ).joinToString(FIELD_SEP)
        val items = page.items.joinToString(ITEM_SEP) { encodeItem(it) }
        return if (items.isEmpty()) header else "$header$FIELD_SEP$items"
    }

    fun decodePage(raw: String): WidgetPanelPage? {
        var cursor = 0
        fun nextHeaderField(): String? {
            if (cursor > raw.length) return null
            val sep = raw.indexOf(FIELD_SEP, cursor)
            return if (sep < 0) {
                raw.substring(cursor).also { cursor = raw.length }
            } else {
                raw.substring(cursor, sep).also { cursor = sep + 1 }
            }
        }

        val id = nextHeaderField()?.toLongOrNull() ?: return null
        val name = nextHeaderField() ?: return null
        val columns = nextHeaderField()?.toIntOrNull()?.coerceIn(2, 20) ?: return null
        val rows = nextHeaderField()?.toIntOrNull()?.coerceIn(3, 40) ?: return null
        val alpha = nextHeaderField()?.toFloatOrNull()?.coerceIn(0.2f, 0.95f) ?: 0.55f
        val blur = nextHeaderField()?.toBooleanStrictOrNull() ?: true
        val visibleRows = nextHeaderField()?.toIntOrNull()?.coerceIn(1, 40) ?: 6
        val cellWidth = nextHeaderField()?.toIntOrNull()?.coerceIn(20, 200) ?: 62
        val marginLeft = nextHeaderField()?.toIntOrNull()?.coerceIn(0, 500) ?: 18
        val marginTop = nextHeaderField()?.toIntOrNull()?.coerceIn(0, 500) ?: 100
        val itemsRaw = if (cursor < raw.length) raw.substring(cursor) else ""
        val items = if (itemsRaw.isNotBlank()) {
            itemsRaw.split(ITEM_SEP).mapNotNull { decodeItem(it) }
        } else {
            emptyList()
        }
        return WidgetPanelPage(id, name, columns, rows, visibleRows, cellWidth, marginLeft, marginTop, items, alpha, blur)
    }

    private fun encodeItem(item: WidgetPanelItem): String =
        listOf(
            item.appWidgetId.toString(),
            item.x.toString(),
            item.y.toString(),
            item.spanX.toString(),
            item.spanY.toString(),
            item.label,
        ).joinToString(FIELD_SEP)

    private fun decodeItem(raw: String): WidgetPanelItem? {
        val parts = raw.split(FIELD_SEP, limit = 6)
        if (parts.size < 5) return null
        val id = parts[0].toIntOrNull() ?: return null
        val x = parts[1].toIntOrNull() ?: return null
        val y = parts[2].toIntOrNull() ?: return null
        val spanX = parts[3].toIntOrNull()?.coerceAtLeast(1) ?: return null
        val spanY = parts[4].toIntOrNull()?.coerceAtLeast(1) ?: return null
        val label = parts.getOrNull(5).orEmpty()
        return WidgetPanelItem(id, x, y, spanX, spanY, label)
    }

    fun encodeAll(pages: List<WidgetPanelPage>): Set<String> =
        if (pages.isEmpty()) emptySet() else setOf(pages.joinToString(PAGE_SEP) { encodePage(it) })

    fun decodeAll(raw: Set<String>): List<WidgetPanelPage> {
        if (raw.isEmpty()) return emptyList()
        val decoded = if (raw.size == 1) {
            val only = raw.first()
            if (PAGE_SEP in only) {
                only.split(PAGE_SEP).mapNotNull { decodePage(it) }
            } else {
                listOfNotNull(decodePage(only))
            }
        } else {
            raw.mapNotNull { decodePage(it) }
        }
        return decoded
    }
}
