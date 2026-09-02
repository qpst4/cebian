package com.slideindex.app.copy

/**
 * Portions derived from EdgeGesture (https://github.com/evilgodxu/EdgeGesture)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

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

    /**
     * 快速启动器等全屏 overlay 会使 [AccessibilityService.rootInActiveWindow] 指向本应用；
     * 此时改从非本包的 [TYPE_APPLICATION] 窗口采集下层宿主文本。
     */
    fun collectAllFromService(service: AccessibilityService): List<UniversalCopyBlock> {
        val selfPackage = service.packageName
        val activeRoot = service.rootInActiveWindow
        val activePackage = activeRoot?.packageName?.toString()
        if (!activePackage.isNullOrBlank() && activePackage != selfPackage) {
            return collectAll(activeRoot)
        }

        val merged = mutableListOf<UniversalCopyBlock>()
        val seen = LinkedHashSet<String>()
        for (window in service.windows) {
            when (window.type) {
                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
                AccessibilityWindowInfo.TYPE_INPUT_METHOD,
                -> continue
            }
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = window.root ?: continue
            val packageName = root.packageName?.toString()
            if (packageName.isNullOrBlank() || packageName == selfPackage) {
                releaseNode(root)
                continue
            }
            try {
                for (block in collectAll(root)) {
                    val key = "${block.bounds}|${block.text}"
                    if (seen.add(key)) merged += block
                }
            } finally {
                releaseNode(root)
            }
        }
        if (merged.isEmpty()) {
            return collectAll(activeRoot)
        }
        merged.sortWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        return merged
    }

    private fun releaseNode(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        @Suppress("DEPRECATION")
        node.recycle()
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
