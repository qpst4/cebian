package com.slideindex.app.overlay.corner

import androidx.compose.ui.geometry.Offset
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.CornerRadialMenuCodec
import kotlin.math.hypot
import kotlin.math.max

internal object CornerRadialMenuGeometry {
    fun bubbleRadiusPx(settings: CornerGestureSettings, density: Float, @Suppress("UNUSED_PARAMETER") slotIndex: Int): Float =
        CornerWheelLayout.bubbleRadiusPx(settings, density)

    fun bubbleCenterForSlot(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        slotIndex: Int,
        settings: CornerGestureSettings,
        density: Float,
    ): Offset = CornerWheelLayout.bubbleCenterForSlot(
        anchor = anchor,
        anchorX = anchorX,
        anchorY = anchorY,
        globalIndex = slotIndex,
        settings = settings,
        density = density,
    )

    fun lastVisibleSlotIndex(activeLayerCount: Int): Int = when (activeLayerCount.coerceIn(1, 3)) {
        1 -> 2
        2 -> 7
        else -> CornerRadialMenuCodec.SLOT_COUNT - 1
    }

    fun slotIndexAt(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        fingerX: Float,
        fingerY: Float,
        settings: CornerGestureSettings,
        density: Float,
        slots: List<GestureAction>,
        editMode: Boolean,
        activeLayerCount: Int,
        revealProgress: Float = 1f,
    ): Int? {
        val lastSlot = if (editMode) {
            CornerRadialMenuCodec.SLOT_COUNT - 1
        } else {
            lastVisibleSlotIndex(activeLayerCount)
        }
        val progress = revealProgress.coerceIn(0f, 1f)
        val hitScale = if (editMode) 1.55f else 1.35f
        var bestSlot = -1
        var bestDistance = Float.MAX_VALUE
        // 由外向内遍历，避免内层气泡在重叠区抢走外层命中。
        for (index in lastSlot downTo 0) {
            val action = slots.getOrElse(index) { GestureAction.None }
            if (!editMode && action is GestureAction.None) continue
            val bubbleRadius = bubbleRadiusPx(settings, density, index)
            val hitSlop = bubbleRadius * hitScale
            val target = bubbleCenterForSlot(anchor, anchorX, anchorY, index, settings, density)
            val centerX = anchorX + (target.x - anchorX) * progress
            val centerY = anchorY + (target.y - anchorY) * progress
            val distance = hypot(fingerX - centerX, fingerY - centerY)
            if (distance <= hitSlop && distance < bestDistance) {
                bestDistance = distance
                bestSlot = index
            }
        }
        return bestSlot.takeIf { it >= 0 }
    }

    fun displayLayerCount(activeLayerCount: Int, highlightedSlot: Int): Int {
        if (highlightedSlot < 0) return activeLayerCount.coerceIn(1, 3)
        val highlightLayer = CornerRadialMenuCodec.layerOf(highlightedSlot) + 1
        return max(activeLayerCount, highlightLayer).coerceIn(1, 3)
    }

    fun isEditButtonHit(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        settings: CornerGestureSettings,
        fingerX: Float,
        fingerY: Float,
        density: Float,
    ): Boolean = CornerWheelLayout.isEditButtonHit(
        anchor = anchor,
        anchorX = anchorX,
        anchorY = anchorY,
        settings = settings,
        fingerX = fingerX,
        fingerY = fingerY,
        density = density,
    )

    fun inwardSlopDistance(
        @Suppress("UNUSED_PARAMETER") anchor: CornerAnchor,
        startX: Float,
        startY: Float,
        currentX: Float,
        currentY: Float,
    ): Float = hypot(currentX - startX, currentY - startY)
}
