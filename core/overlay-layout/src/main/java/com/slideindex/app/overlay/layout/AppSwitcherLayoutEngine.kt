package com.slideindex.app.overlay.layout

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class AppSwitcherSide {
    LEFT,
    RIGHT,
}

data class AppSwitcherSlotLayout(
    val index: Int,
    val centerX: Float,
    val centerY: Float,
    val hitRadius: Float,
)

data class AppSwitcherPanelLayout(
    val side: AppSwitcherSide,
    val anchorX: Float,
    val anchorY: Float,
    val itemSizePx: Float,
    val slots: List<AppSwitcherSlotLayout>,
)

object AppSwitcherLayoutEngine {
    private const val ARC_DEGREES = 180.0
    private const val RADIUS_STEP_RATIO = 1.25f
    private const val DEFAULT_INITIAL_RADIUS_RATIO = 1.02f
    private const val DEFAULT_ITEM_SPACING_RATIO = 1.12f
    private const val EDGE_PADDING_PX = 16f
    private const val CORNER_SAFE_PADDING_PX = 56f
    private const val HIT_SCALE = 1.35f

    fun layout(
        itemCount: Int,
        side: AppSwitcherSide,
        anchorRawY: Float,
        screenWidth: Float,
        screenHeight: Float,
        itemSizeDp: Float,
        spacingDp: Float,
        density: Float,
        initialRadiusRatio: Float = DEFAULT_INITIAL_RADIUS_RATIO,
        itemSpacingRatio: Float = DEFAULT_ITEM_SPACING_RATIO,
    ): AppSwitcherPanelLayout {
        val itemSizePx = itemSizeDp.coerceAtLeast(1f) * density
        val targetSpacing = itemSizePx * itemSpacingRatio.coerceAtLeast(0.1f)
        val offsets = sectorItemOffsets(
            itemCount = itemCount.coerceAtLeast(1),
            itemSizePx = itemSizePx,
            side = side,
            initialRadiusRatio = initialRadiusRatio.coerceAtLeast(0.1f),
            itemSpacingRatio = itemSpacingRatio.coerceAtLeast(0.1f),
        )
        val anchor = sectorAnchor(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            anchorRawY = anchorRawY,
            side = side,
            itemOffsets = offsets,
            itemSizePx = itemSizePx,
        )
        val hitRadius = itemSizePx * 0.5f * HIT_SCALE
        val slots = offsets.mapIndexed { index, offset ->
            AppSwitcherSlotLayout(
                index = index,
                centerX = anchor.x + offset.x,
                centerY = anchor.y + offset.y,
                hitRadius = hitRadius,
            )
        }
        return AppSwitcherPanelLayout(
            side = side,
            anchorX = anchor.x,
            anchorY = anchor.y,
            itemSizePx = itemSizePx,
            slots = slots,
        )
    }

    fun slotIndexAt(
        layout: AppSwitcherPanelLayout,
        fingerX: Float,
        fingerY: Float,
    ): Int {
        var bestIndex = -1
        var bestDistance = Float.MAX_VALUE
        for (slot in layout.slots) {
            val distance = hypot(fingerX - slot.centerX, fingerY - slot.centerY)
            if (distance <= slot.hitRadius && distance < bestDistance) {
                bestDistance = distance
                bestIndex = slot.index
            }
        }
        return bestIndex
    }

    fun isOutsidePanel(
        layout: AppSwitcherPanelLayout,
        fingerX: Float,
        fingerY: Float,
        marginPx: Float,
    ): Boolean {
        if (layout.slots.isEmpty()) return true
        val minX = layout.slots.minOf { it.centerX - it.hitRadius } - marginPx
        val maxX = layout.slots.maxOf { it.centerX + it.hitRadius } + marginPx
        val minY = layout.slots.minOf { it.centerY - it.hitRadius } - marginPx
        val maxY = layout.slots.maxOf { it.centerY + it.hitRadius } + marginPx
        return fingerX < minX || fingerX > maxX || fingerY < minY || fingerY > maxY
    }

    private data class Offset(val x: Float, val y: Float)

    private data class SectorLayerLayout(
        val radius: Float,
        val angles: List<Double>,
    )

    private data class SectorLayerCandidate(
        val layouts: List<SectorLayerLayout>,
        val countScore: Int,
        val distanceScore: Float,
    )

    private fun sectorItemOffsets(
        itemCount: Int,
        itemSizePx: Float,
        side: AppSwitcherSide,
        initialRadiusRatio: Float,
        itemSpacingRatio: Float,
    ): List<Offset> {
        if (itemCount <= 0 || itemSizePx <= 0f) return emptyList()
        val targetSpacing = itemSizePx * itemSpacingRatio
        val layers = sectorLayerLayouts(itemCount, itemSizePx, initialRadiusRatio, itemSpacingRatio)
        val offsets = ArrayList<Offset>(itemCount)
        layers.forEach { layer ->
            layer.angles.forEach { angle ->
                val inward = cos(angle) * layer.radius
                val cross = sin(angle) * layer.radius
                offsets += when (side) {
                    AppSwitcherSide.LEFT -> Offset(inward.toFloat(), cross.toFloat())
                    AppSwitcherSide.RIGHT -> Offset((-inward).toFloat(), cross.toFloat())
                }
            }
        }
        return offsets
    }

    private fun sectorAnchor(
        screenWidth: Float,
        screenHeight: Float,
        anchorRawY: Float,
        side: AppSwitcherSide,
        itemOffsets: List<Offset>,
        itemSizePx: Float,
    ): Offset {
        if (screenWidth <= 0f || screenHeight <= 0f) return Offset(0f, 0f)
        val itemSizeHalf = itemSizePx / 2f
        val minX = itemOffsets.minOfOrNull { it.x } ?: 0f
        val maxX = itemOffsets.maxOfOrNull { it.x } ?: 0f
        val minY = itemOffsets.minOfOrNull { it.y } ?: 0f
        val maxY = itemOffsets.maxOfOrNull { it.y } ?: 0f
        val edgeX = when (side) {
            AppSwitcherSide.LEFT -> EDGE_PADDING_PX + itemSizeHalf - minX
            AppSwitcherSide.RIGHT -> screenWidth - EDGE_PADDING_PX - itemSizeHalf - maxX
        }
        val anchorY = anchorRawY.coerceInSafely(
            minimumValue = CORNER_SAFE_PADDING_PX + itemSizeHalf - minY,
            maximumValue = screenHeight - CORNER_SAFE_PADDING_PX - itemSizeHalf - maxY,
        )
        return Offset(edgeX, anchorY)
    }

    private fun sectorLayerLayouts(
        itemCount: Int,
        itemSizePx: Float,
        initialRadiusRatio: Float,
        itemSpacingRatio: Float,
    ): List<SectorLayerLayout> {
        if (itemCount <= 0 || itemSizePx <= 0f) return emptyList()
        val targetSpacing = itemSizePx * itemSpacingRatio
        val firstLayerCapacity = sectorLayerCapacity(0, itemSizePx, targetSpacing, initialRadiusRatio)
        if (itemCount <= firstLayerCapacity) {
            val radius = sectorSingleLayerRadius(itemCount, itemSizePx, targetSpacing, initialRadiusRatio)
            return listOf(
                SectorLayerLayout(
                    radius = radius,
                    angles = sectorLayerAngles(itemCount, radius, itemSizePx, targetSpacing),
                ),
            )
        }
        val capacities = ArrayList<Int>()
        var layerCount = 1
        var bestCandidate: SectorLayerCandidate? = null
        while (layerCount <= itemCount) {
            capacities += sectorLayerCapacity(layerCount - 1, itemSizePx, targetSpacing, initialRadiusRatio)
            val counts = sectorLayerCountsOrNull(itemCount, capacities) ?: run {
                layerCount++
                continue
            }
            val layouts = counts.mapIndexed { layer, count ->
                val radius = sectorLayerRadius(layer, itemSizePx, targetSpacing, initialRadiusRatio)
                SectorLayerLayout(
                    radius = radius,
                    angles = sectorLayerAngles(count, radius, itemSizePx, targetSpacing),
                )
            }
            val candidate = SectorLayerCandidate(
                layouts = layouts,
                countScore = sectorLayerCountScore(counts),
                distanceScore = sectorLayerDistanceScore(layouts, targetSpacing),
            )
            if (bestCandidate == null ||
                candidate.countScore < bestCandidate.countScore ||
                (candidate.countScore == bestCandidate.countScore &&
                    candidate.distanceScore < bestCandidate.distanceScore)
            ) {
                bestCandidate = candidate
            }
            layerCount++
        }
        return bestCandidate?.layouts ?: run {
            val radius = sectorLayerRadius(0, itemSizePx, targetSpacing, initialRadiusRatio)
            listOf(SectorLayerLayout(radius, sectorLayerAngles(itemCount, radius, itemSizePx, targetSpacing)))
        }
    }

    private fun sectorLayerCountsOrNull(itemCount: Int, capacities: List<Int>): List<Int>? {
        val layerCount = capacities.size
        if (layerCount == 1) {
            return if (itemCount <= capacities.first()) listOf(itemCount) else null
        }
        val minRequiredCount = layerCount * (layerCount + 1) / 2
        if (itemCount < minRequiredCount) return null
        val memo = mutableMapOf<Triple<Int, Int, Int>, List<Int>?>()
        return sectorLayerCountsRecursive(0, 0, itemCount, capacities, memo)
    }

    private fun sectorLayerCountsRecursive(
        index: Int,
        previousCount: Int,
        remainingCount: Int,
        capacities: List<Int>,
        memo: MutableMap<Triple<Int, Int, Int>, List<Int>?>,
    ): List<Int>? {
        val key = Triple(index, previousCount, remainingCount)
        memo[key]?.let { return it }
        if (index == capacities.size) {
            return if (remainingCount == 0) emptyList() else null
        }
        val remainingLayerCount = capacities.size - index - 1
        val minCount = previousCount + 1
        val maxCount = min(capacities[index], remainingCount)
        var bestCounts: List<Int>? = null
        var bestScore: Int? = null
        for (count in minCount..maxCount) {
            val minRemainingCount = remainingLayerCount * count +
                remainingLayerCount * (remainingLayerCount + 1) / 2
            if (remainingCount - count < minRemainingCount) continue
            val nextCounts = sectorLayerCountsRecursive(
                index + 1,
                count,
                remainingCount - count,
                capacities,
                memo,
            ) ?: continue
            val counts = listOf(count) + nextCounts
            val score = sectorLayerCountScore(counts)
            if (bestScore == null || score < bestScore) {
                bestScore = score
                bestCounts = counts
            }
        }
        memo[key] = bestCounts
        return bestCounts
    }

    private fun sectorLayerCountScore(counts: List<Int>): Int {
        if (counts.size <= 1) return 0
        return counts.zipWithNext().sumOf { (inner, outer) ->
            val diff = outer - inner - 1
            diff * diff
        }
    }

    private fun sectorLayerDistanceScore(
        layouts: List<SectorLayerLayout>,
        targetSpacing: Float,
    ): Float {
        val points = layouts.flatMap { layer ->
            layer.angles.map { angle ->
                Offset(
                    x = (cos(angle) * layer.radius).toFloat(),
                    y = (sin(angle) * layer.radius).toFloat(),
                )
            }
        }
        if (points.size <= 1) return 0f
        val nearestDistances = points.mapIndexed { index, point ->
            points.indices
                .filter { it != index }
                .minOf { otherIndex ->
                    val other = points[otherIndex]
                    hypot(point.x - other.x, point.y - other.y)
                }
        }
        val distanceRange = (nearestDistances.maxOrNull() ?: 0f) - (nearestDistances.minOrNull() ?: 0f)
        val averageDistance = nearestDistances.average().toFloat()
        val spacingDelta = averageDistance - targetSpacing
        return distanceRange + spacingDelta * spacingDelta / targetSpacing.coerceAtLeast(1f)
    }

    private fun sectorLayerRadius(
        layer: Int,
        itemSizePx: Float,
        targetSpacing: Float,
        initialRadiusRatio: Float,
    ): Float = itemSizePx * initialRadiusRatio + layer * targetSpacing

    private fun sectorSingleLayerRadius(
        count: Int,
        itemSizePx: Float,
        targetSpacing: Float,
        initialRadiusRatio: Float,
    ): Float {
        val baseRadius = sectorLayerRadius(0, itemSizePx, targetSpacing, initialRadiusRatio)
        if (count <= 1) return baseRadius
        fun satisfiesSpacing(radius: Float): Boolean {
            val preferredAngle = sectorPreferredAngleStep(radius, targetSpacing) * (count - 1)
            return preferredAngle <= sectorAvailableAngle(radius, itemSizePx)
        }
        if (satisfiesSpacing(baseRadius)) return baseRadius
        var low = baseRadius
        var high = baseRadius
        while (!satisfiesSpacing(high) && high < itemSizePx * 32f) {
            high *= RADIUS_STEP_RATIO
        }
        if (!satisfiesSpacing(high)) return high
        repeat(20) {
            val mid = (low + high) / 2f
            if (satisfiesSpacing(mid)) {
                high = mid
            } else {
                low = mid
            }
        }
        return high
    }

    private fun sectorLayerAngles(
        count: Int,
        radius: Float,
        itemSizePx: Float,
        targetSpacing: Float,
    ): List<Double> {
        if (count <= 0) return emptyList()
        if (count == 1) return listOf(0.0)
        val availableAngle = sectorAvailableAngle(radius, itemSizePx)
        val preferredAngleStep = sectorPreferredAngleStep(radius, targetSpacing)
        val preferredAngle = preferredAngleStep * (count - 1)
        val angleStep = if (preferredAngle <= availableAngle) {
            preferredAngleStep
        } else {
            availableAngle / (count - 1)
        }
        val startAngle = -angleStep * (count - 1) / 2.0
        return List(count) { index -> startAngle + angleStep * index }
    }

    private fun sectorLayerCapacity(
        layer: Int,
        itemSizePx: Float,
        targetSpacing: Float,
        initialRadiusRatio: Float,
    ): Int {
        val radius = sectorLayerRadius(layer, itemSizePx, targetSpacing, initialRadiusRatio)
        val availableAngle = sectorAvailableAngle(radius, itemSizePx)
        val angleStep = sectorPreferredAngleStep(radius, targetSpacing)
        if (angleStep <= 0.0) return 1
        return floor(availableAngle / angleStep).toInt().coerceAtLeast(0) + 1
    }

    private fun sectorAvailableAngle(radius: Float, itemSizePx: Float): Double {
        val halfSectorAngle = Math.toRadians(ARC_DEGREES) / 2.0
        val edgeSafeAngle = min(
            halfSectorAngle,
            acos((itemSizePx / 2f / radius).coerceIn(0f, 1f).toDouble()),
        )
        return edgeSafeAngle * 2.0
    }

    private fun sectorPreferredAngleStep(radius: Float, targetSpacing: Float): Double =
        2.0 * asin((targetSpacing / (2f * radius)).coerceIn(0f, 1f).toDouble())

    private fun Float.coerceInSafely(minimumValue: Float, maximumValue: Float): Float =
        if (minimumValue <= maximumValue) coerceIn(minimumValue, maximumValue)
        else (minimumValue + maximumValue) / 2f
}
