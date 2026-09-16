package com.slideindex.app.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.hypot

internal data class FloatingPointerInjectedSwipePreview(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val startedAtMs: Long,
    val swipeDurationMs: Long,
    val fadeDurationMs: Long,
) {
    fun isExpired(nowMs: Long): Boolean =
        nowMs - startedAtMs >= swipeDurationMs + fadeDurationMs

    fun growHead(nowMs: Long): Offset {
        val elapsed = (nowMs - startedAtMs).coerceAtLeast(0L)
        val grow = (elapsed.toFloat() / swipeDurationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
        return Offset(
            x = startX + (endX - startX) * grow,
            y = startY + (endY - startY) * grow
        )
    }

    /** After the swipe segment is full, the tail stays fixed while the head retreats to the start. */
    fun retreatFrom(nowMs: Long): Offset? {
        val elapsed = nowMs - startedAtMs
        if (elapsed < swipeDurationMs) return null
        val retreat = ((elapsed - swipeDurationMs).toFloat() / fadeDurationMs.coerceAtLeast(1L))
            .coerceIn(0f, 1f)
        return Offset(
            x = startX + (endX - startX) * retreat,
            y = startY + (endY - startY) * retreat
        )
    }
}

internal fun DrawScope.drawInjectedSwipePreview(
    preview: FloatingPointerInjectedSwipePreview,
    nowMs: Long,
    colorArgb: Int,
    strokeWidthPx: Float
) {
    if (preview.isExpired(nowMs)) return
    val color = Color(colorArgb)
    val strokeWidth = strokeWidthPx.coerceAtLeast(1f)
    val retreatFrom = preview.retreatFrom(nowMs)
    val (from, to) = if (retreatFrom != null) {
        retreatFrom to Offset(preview.endX, preview.endY)
    } else {
        Offset(preview.startX, preview.startY) to preview.growHead(nowMs)
    }
    if (hypot((to.x - from.x).toDouble(), (to.y - from.y).toDouble()) < 0.5) return
    drawQcTrailSegment(
        from = from,
        to = to,
        ageFraction = 0f,
        color = color,
        strokeBasePx = strokeWidth
    )
}
