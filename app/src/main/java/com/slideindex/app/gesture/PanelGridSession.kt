package com.slideindex.app.gesture

import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherItem
class PanelGridSession {
    val cellBounds = mutableListOf<Pair<Any, android.graphics.RectF>>()
    var highlightedIndex: Int = -1
        private set

    fun reset() {
        cellBounds.clear()
        highlightedIndex = -1
    }

    fun updateHighlight(localX: Float, localY: Float): Int {
        cellBounds.forEachIndexed { index, (_, rect) ->
            if (rect.contains(localX, localY)) {
                if (highlightedIndex != index) highlightedIndex = index
                return index
            }
        }
        if (highlightedIndex != -1) highlightedIndex = -1
        return -1
    }

    fun highlightedQuickItem(): QuickLauncherItem? {
        return cellBounds.getOrNull(highlightedIndex)?.first as? QuickLauncherItem
    }
}
