package com.slideindex.app.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import com.slideindex.app.service.AccessibilityTextExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * FV-style preview bounds cache: async full-tree scan (G4), per-frame point hit-test (o1.r).
 */
object FloatBallPreviewBoundsCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entriesRef = AtomicReference<List<AccessibilityTextExtractor.PreviewBoundsEntry>>(emptyList())
    private val editableEntriesRef =
        AtomicReference<List<AccessibilityTextExtractor.PreviewBoundsEntry>>(emptyList())
    @Volatile
    private var cacheEra = 0

    fun invalidate() {
        cacheEra++
        entriesRef.set(emptyList())
        editableEntriesRef.set(emptyList())
    }

    fun isReady(): Boolean = entriesRef.get().isNotEmpty()

    fun hitTestAt(rawX: Float, rawY: Float): Rect? {
        return AccessibilityTextExtractor.hitTestPreviewBounds(
            entries = entriesRef.get(),
            px = rawX.toInt(),
            py = rawY.toInt()
        )
    }

    fun hitTestEditableAt(rawX: Float, rawY: Float): Rect? {
        return AccessibilityTextExtractor.hitTestEditableBounds(
            entries = editableEntriesRef.get(),
            px = rawX.toInt(),
            py = rawY.toInt()
        )
    }

    fun refresh(
        service: AccessibilityService,
        onReady: (() -> Unit)? = null
    ) {
        val era = cacheEra
        scope.launch {
            val built = runInterruptible {
                val preview = AccessibilityTextExtractor.collectPreviewBoundsCache(service)
                val editable = AccessibilityTextExtractor.collectEditableBoundsCache(service)
                preview to editable
            }
            if (era != cacheEra) return@launch
            entriesRef.set(built.first)
            editableEntriesRef.set(built.second)
            if (onReady != null) {
                withContext(Dispatchers.Main) {
                    if (era == cacheEra) {
                        onReady()
                    }
                }
            }
        }
    }
}
