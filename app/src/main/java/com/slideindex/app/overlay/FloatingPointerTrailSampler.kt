package com.slideindex.app.overlay

import com.slideindex.app.settings.FloatingPointerTrailType
import kotlin.math.hypot

internal data class FloatingPointerTrailThresholds(
    val minTimeMs: Long,
    val maxTimeMs: Long,
    val minDistPx: Float,
    val maxDistPx: Float
)

internal object FloatingPointerTrailSampler {
    /** Quick Cursor low/simple trail sampling thresholds. */
    fun thresholdsFor(type: FloatingPointerTrailType, density: Float): FloatingPointerTrailThresholds? =
        when (type) {
            FloatingPointerTrailType.OFF -> null
            FloatingPointerTrailType.SIMPLE -> FloatingPointerTrailThresholds(
                minTimeMs = 2L,
                maxTimeMs = 50L,
                minDistPx = 3f * density,
                maxDistPx = 18f * density
            )
            FloatingPointerTrailType.HIGH_DETAIL -> FloatingPointerTrailThresholds(
                minTimeMs = 1L,
                maxTimeMs = 25L,
                minDistPx = 1f,
                maxDistPx = 2f * density
            )
        }

    /**
     * Quick Cursor records an anchor + head pair, then commits the head when movement exceeds
     * time/distance thresholds (`ar.java` + `o81.java`).
     */
    fun recordPoint(
        points: MutableList<FloatingPointerTrailPoint>,
        x: Float,
        y: Float,
        nowMs: Long,
        type: FloatingPointerTrailType,
        density: Float
    ) {
        if (type == FloatingPointerTrailType.OFF) return
        val thresholds = thresholdsFor(type, density) ?: return

        if (points.isEmpty()) {
            points.add(FloatingPointerTrailPoint(x, y, nowMs))
            points.add(FloatingPointerTrailPoint(x, y, nowMs))
            return
        }

        val head = points.last()
        val anchor = points[points.lastIndex - 1]
        val elapsedMs = nowMs - head.timeMs
        val distancePx = hypot(
            (anchor.x - head.x).toDouble(),
            (anchor.y - head.y).toDouble()
        ).toFloat()

        val shouldExtend = elapsedMs <= thresholds.minTimeMs ||
            distancePx <= thresholds.minDistPx ||
            (elapsedMs <= thresholds.maxTimeMs && distancePx <= thresholds.maxDistPx)

        if (shouldExtend) {
            points[points.lastIndex] = head.copy(x = x, y = y)
        } else {
            points.add(FloatingPointerTrailPoint(x, y, nowMs))
        }

        while (points.size > 128) {
            points.removeAt(0)
        }
    }
}
