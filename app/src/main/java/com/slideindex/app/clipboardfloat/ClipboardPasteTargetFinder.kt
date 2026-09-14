package com.slideindex.app.clipboardfloat

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import com.slideindex.app.service.AccessibilityTextExtractor

/** FV-style paste targets: visible editables, smallest rect wins when nested. */
object ClipboardPasteTargetFinder {

    fun findTargetRects(service: AccessibilityService): List<Rect> {
        val entries = AccessibilityTextExtractor.collectEditableBoundsCache(service)
        if (entries.isEmpty()) return emptyList()
        val rects = entries.map { Rect(it.rect) }
        return dedupeNestedTargets(rects).distinctBy { rectKey(it) }
    }

    private fun rectKey(rect: Rect): String =
        "${rect.left},${rect.top},${rect.right},${rect.bottom}"

    internal fun dedupeNestedTargets(rects: List<Rect>): List<Rect> {
        if (rects.size <= 1) return rects
        val sorted = rects.sortedBy { it.width().coerceAtLeast(1) * it.height().coerceAtLeast(1) }
        val kept = ArrayList<Rect>(sorted.size)
        for (candidate in sorted) {
            if (kept.any { outer -> outer != candidate && outer.contains(candidate) }) {
                continue
            }
            kept.add(candidate)
        }
        return kept
    }
}
