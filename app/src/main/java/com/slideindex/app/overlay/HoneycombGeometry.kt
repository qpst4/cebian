package com.slideindex.app.overlay

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal object HoneycombGeometry {
    data class Point(val x: Float, val y: Float)

    private const val SQRT_THREE_OVER_TWO = 0.8660254f

    fun compactPoints(count: Int, pitch: Float): List<Point> {
        if (count <= 0 || pitch <= 0f) return emptyList()
        val capacities = circularRowCapacities(count)
        val result = ArrayList<Point>(count)
        val verticalPitch = SQRT_THREE_OVER_TWO * pitch
        val centerRow = (capacities.size - 1) * 0.5f
        for (row in capacities.indices) {
            val capacity = capacities[row]
            val y = (row - centerRow) * verticalPitch
            val latticeRow = row - capacities.size / 2
            val stagger = if (latticeRow % 2 == 0) 0f else 0.5f
            val rowPoints = ArrayList<Point>(capacity + 4)
            for (column in (-capacity - 2)..(capacity + 2)) {
                rowPoints += Point((column + stagger) * pitch, y)
            }
            rowPoints.sortBy { it.x * it.x + it.y * it.y }
            result += rowPoints.take(capacity)
        }
        recenter(result)
        result.sortWith(
            compareBy<Point> { squaredRadius(it) }
                .thenBy { atan2(it.y.toDouble(), it.x.toDouble()) },
        )
        return result
    }

    private fun recenter(points: MutableList<Point>) {
        if (points.isEmpty()) return
        var sumX = 0f
        var sumY = 0f
        points.forEach { point ->
            sumX += point.x
            sumY += point.y
        }
        val centerX = sumX / points.size
        val centerY = sumY / points.size
        for (index in points.indices) {
            val point = points[index]
            points[index] = Point(point.x - centerX, point.y - centerY)
        }
    }

    private fun circularRowCapacities(count: Int): IntArray {
        val maximumRows = max(1, min(count, ceil(sqrt(count.toFloat()) * 1.65f).toInt() + 2))
        var best: IntArray? = null
        var bestScore = Float.MAX_VALUE
        var rows = 1
        while (rows <= maximumRows) {
            if (rows <= count && (rows % 2 != 0 || count % 2 == 0)) {
                val candidate = allocateCircularRows(count, rows)
                val maximumCapacity = candidate.maxOrNull() ?: 1
                val width = maximumCapacity.toFloat()
                val height = (rows - 1) * SQRT_THREE_OVER_TWO + 1f
                val aspectPenalty = abs(kotlin.math.ln(width / height))
                val targetRows = sqrt(count.toFloat()) * 1.2f
                val densityPenalty = abs(rows - targetRows) * 0.018f
                val score = aspectPenalty + densityPenalty
                if (score < bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
            rows++
        }
        return best ?: intArrayOf(count)
    }

    private fun allocateCircularRows(count: Int, rows: Int): IntArray {
        val capacities = IntArray(rows) { 1 }
        val targets = FloatArray(rows)
        val center = (rows - 1) * 0.5f
        val radius = max(0.5f, rows * 0.5f)
        var weightSum = 0f
        for (row in 0 until rows) {
            val normalized = (row - center) / radius
            targets[row] = sqrt(max(0f, 1f - normalized * normalized))
            weightSum += targets[row]
        }
        var remaining = count - rows
        for (row in 0 until rows) {
            targets[row] = (remaining * targets[row] / max(0.001f, weightSum)) + 1f
        }
        while (remaining > 0) {
            var bestRow = 0
            var bestDeficit = Float.NEGATIVE_INFINITY
            var bestMirrorImbalance = Int.MAX_VALUE
            for (row in 0 until rows) {
                val deficit = targets[row] - capacities[row]
                val mirror = (rows - 1) - row
                val mirrorImbalance = abs((capacities[row] + 1) - capacities[mirror])
                if (deficit > bestDeficit + 1e-4f ||
                    (abs(deficit - bestDeficit) <= 1e-4f && mirrorImbalance < bestMirrorImbalance)
                ) {
                    bestDeficit = deficit
                    bestRow = row
                    bestMirrorImbalance = mirrorImbalance
                }
            }
            capacities[bestRow]++
            remaining--
        }
        return capacities
    }

    private fun squaredRadius(point: Point): Float = point.x * point.x + point.y * point.y

    fun smoothScale(distance: Float, radius: Float, centerScale: Float, edgeScale: Float): Float {
        if (radius <= 0f) return edgeScale
        val t = clamp(distance / radius, 0f, 1f)
        val smooth = t * t * (3f - 2f * t)
        return (edgeScale - centerScale) * smooth + centerScale
    }

    fun hitScaled(
        centers: List<Point>,
        x: Float,
        y: Float,
        iconSize: Float,
        effectCenterX: Float,
        effectCenterY: Float,
        effectRadius: Float,
        centerScale: Float,
        edgeScale: Float,
    ): Int {
        var best = -1
        var bestNormalized = Float.MAX_VALUE
        centers.forEachIndexed { index, point ->
            if (!point.x.isFinite() || !point.y.isFinite()) return@forEachIndexed
            val effectDistance = hypot(point.x - effectCenterX, point.y - effectCenterY)
            val scale = smoothScale(effectDistance, effectRadius, centerScale, edgeScale)
            val hitRadius = max(8f, iconSize * scale * 0.58f)
            val dx = x - point.x
            val dy = y - point.y
            val normalized = (dx * dx + dy * dy) / (hitRadius * hitRadius)
            if (normalized <= 1f && normalized < bestNormalized) {
                bestNormalized = normalized
                best = index
            }
        }
        return best
    }

    fun hexagonPath(centerX: Float, centerY: Float, radius: Float): android.graphics.Path {
        val path = android.graphics.Path()
        for (vertex in 0 until 6) {
            val angle = (PI / 3.0 * vertex - PI / 6.0).toFloat()
            val x = centerX + radius * cos(angle)
            val y = centerY + radius * sin(angle)
            if (vertex == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun clamp(value: Float, minValue: Float, maxValue: Float): Float =
        max(minValue, min(maxValue, value))
}
