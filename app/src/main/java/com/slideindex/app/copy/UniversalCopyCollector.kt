package com.slideindex.app.copy

/**
 * Portions derived from EdgeGesture (https://github.com/evilgodxu/EdgeGesture)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class UniversalCopyBlock(
    val text: String,
    val bounds: Rect,
)

object UniversalCopyCollector {
    private val tempRect = Rect()

    fun collectAll(root: AccessibilityNodeInfo?): List<UniversalCopyBlock> {
        if (root == null) return emptyList()
        val items = mutableListOf<TextItem>()
        traverse(root, items)
        items.sortWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        return deduplicate(items).map { UniversalCopyBlock(it.text, Rect(it.bounds)) }
    }

    private data class TextItem(val text: String, val bounds: Rect)

    private fun traverse(node: AccessibilityNodeInfo, items: MutableList<TextItem>) {
        if (!node.isVisibleToUser) return
        val className = node.className?.toString().orEmpty()
        if (className.contains("Image", ignoreCase = true)) return
        var childrenHadText = false
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val sizeBefore = items.size
            traverse(child, items)
            if (items.size > sizeBefore) childrenHadText = true
        }
        if (childrenHadText) return
        val text = normalizeText(node.text) ?: normalizeText(node.contentDescription) ?: return
        node.getBoundsInScreen(tempRect)
        if (tempRect.isEmpty) return
        items += TextItem(text, Rect(tempRect))
    }

    private fun normalizeText(value: CharSequence?): String? {
        val text = value?.toString()?.trim().orEmpty()
        return text.takeIf { it.isNotEmpty() }
    }

    private fun deduplicate(items: List<TextItem>): List<TextItem> {
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<TextItem>()
        for (item in items) {
            val key = "${item.bounds}|${item.text}"
            if (seen.add(key)) result += item
        }
        return result
    }
}
