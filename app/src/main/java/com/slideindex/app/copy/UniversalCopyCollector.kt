package com.slideindex.app.copy

/**
 * Portions derived from EdgeGesture (https://github.com/evilgodxu/EdgeGesture)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import com.slideindex.app.service.AccessibilityTextExtractor

data class UniversalCopyBlock(
    val text: String,
    val bounds: Rect,
)

object UniversalCopyCollector {
    /**
     * 快速启动器等全屏 overlay 会使 [AccessibilityService.rootInActiveWindow] 指向本应用；
     * 此时改从非本包窗口采集下层宿主文本。
     */
    fun collectAllFromService(service: AccessibilityService): List<UniversalCopyBlock> {
        val selfPackage = service.packageName
        val activeRoot = service.rootInActiveWindow
        val activePackage = activeRoot?.packageName?.toString()
        val blocks = if (!activePackage.isNullOrBlank() && activePackage != selfPackage) {
            AccessibilityTextExtractor.collectScreenTextBlocksFromRoot(activeRoot, service)
        } else {
            val exclude = if (selfPackage.isNullOrBlank()) emptySet() else setOf(selfPackage)
            AccessibilityTextExtractor.collectAllScreenTextBlocks(service, exclude)
        }
        if (blocks.isNotEmpty()) {
            return blocks.map { UniversalCopyBlock(it.text, Rect(it.bounds)) }
        }
        return AccessibilityTextExtractor.collectScreenTextBlocksFromRoot(activeRoot, service)
            .map { UniversalCopyBlock(it.text, Rect(it.bounds)) }
    }
}
