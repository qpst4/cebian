package com.slideindex.app.widget

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
