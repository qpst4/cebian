package com.slideindex.app.overlay.layout

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class FvAppSwitcherSide { LEFT, RIGHT }

data class FvSlotLayout(
    val index: Int,
    val centerX: Float,
    val centerY: Float,
    val angleMaxDeg: Int,
    val angleMinDeg: Int,
    val minDistSq: Int,
    val maxDistSq: Int,
)

data class FvPanelLayout(
    val anchorX: Float,
    val anchorY: Float,
    val side: FvAppSwitcherSide,
    val itemSizePx: Float,
    val toolbarButtonRadiusPx: Float,
    val slots: List<FvSlotLayout>,
    val toolbarCenterX: Float,
    val toolbarButtonCenters: List<Pair<Float, Float>>,
)

/** 按 FV CircleAppContainer 静态几何移植的半圆槽位布局。 */
object FvCircleLayoutEngine {
    const val ICON_SIZE_DP = 36f
    private const val TOOLBAR_BUTTON_RADIUS_DP = 18f
    val LAYER_RADIUS_DP = floatArrayOf(88f, 138f, 188f, 238f)
    private val LAYER_SLOT_COUNTS = intArrayOf(5, 8, 11, 14)
    private const val END_MARGIN_RAD = PI / 180.0 * 6.0
    private const val TOOLBAR_GAP_DP = 10f
    private const val TOOLBAR_EDGE_GAP_DP = 15f
    private const val TOOLBAR_BUTTON_COUNT = 5
    private const val TOOLBAR_HIT_SCALE = 1.35f

    private data class SlotGeo(
        val offsetX: Float,
        val offsetY: Float,
        val angleMaxDeg: Int,
        val angleMinDeg: Int,
        val layerIndex: Int,
    )

    private val allSlotGeo: List<SlotGeo> = buildAllSlotGeometry()

    fun layout(
        circleCount: Int,
        side: FvAppSwitcherSide,
        anchorX: Float,
        anchorY: Float,
        screenWidth: Float,
        density: Float,
    ): FvPanelLayout {
        val itemSizePx = ICON_SIZE_DP * density
        val toolbarButtonRadiusPx = TOOLBAR_BUTTON_RADIUS_DP * density
        val slotCount = slotCountForCircleCount(circleCount)
        val slots = allSlotGeo.take(slotCount).mapIndexed { index, geo ->
            val (cx, cy) = toScreenOffset(geo.offsetX, geo.offsetY, side, anchorX, anchorY, density)
            val (minDistSq, maxDistSq) = radialBandSq(geo.layerIndex, density)
            FvSlotLayout(
                index = index,
                centerX = cx,
                centerY = cy,
                angleMaxDeg = geo.angleMaxDeg,
                angleMinDeg = geo.angleMinDeg,
                minDistSq = minDistSq,
                maxDistSq = maxDistSq,
            )
        }
        val toolbar = buildToolbar(
            side = side,
            anchorY = anchorY,
            screenWidth = screenWidth,
            density = density,
            toolbarRadiusPx = toolbarButtonRadiusPx,
        )
        return FvPanelLayout(
            anchorX = anchorX,
            anchorY = anchorY,
            side = side,
            itemSizePx = itemSizePx,
            toolbarButtonRadiusPx = toolbarButtonRadiusPx,
            slots = slots,
            toolbarCenterX = toolbar.first,
            toolbarButtonCenters = toolbar.second,
        )
    }

    fun slotIndexAt(layout: FvPanelLayout, rawX: Float, rawY: Float): Int {
        val density = if (layout.itemSizePx > 0f) layout.itemSizePx / ICON_SIZE_DP else 1f
        val hitRadius = maxOf(layout.itemSizePx * 0.9f, 24f * density)
        val hitRadiusSq = hitRadius * hitRadius
        var bestIndex = -1
        var bestDistSq = Float.MAX_VALUE
        for (slot in layout.slots) {
            val dx = slot.centerX - rawX
            val dy = slot.centerY - rawY
            val slotDistSq = dx * dx + dy * dy
            if (slotDistSq <= hitRadiusSq && slotDistSq < bestDistSq) {
                bestDistSq = slotDistSq
                bestIndex = slot.index
            }
        }
        if (bestIndex >= 0) return bestIndex

        val relX = relativeX(layout, rawX)
        val relY = rawY - layout.anchorY
        val distSq = (relX * relX + relY * relY).toInt()
        val angleDeg = toAngleDeg(atan2(-relY.toDouble(), relX.toDouble()))
        var bestBandIndex = -1
        var bestBandDistSq = Float.MAX_VALUE
        for (slot in layout.slots) {
            if (angleDeg <= slot.angleMaxDeg &&
                angleDeg >= slot.angleMinDeg &&
                distSq >= slot.minDistSq &&
                distSq <= slot.maxDistSq
            ) {
                val dx = slot.centerX - rawX
                val dy = slot.centerY - rawY
                val slotDistSq = dx * dx + dy * dy
                if (slotDistSq < bestBandDistSq) {
                    bestBandDistSq = slotDistSq
                    bestBandIndex = slot.index
                }
            }
        }
        return bestBandIndex
    }

    fun toolbarButtonAt(layout: FvPanelLayout, rawX: Float, rawY: Float): FvToolbarButton? {
        val hitRadius = toolbarHitRadius(layout)
        var bestButton: FvToolbarButton? = null
        var bestDistSq = Float.MAX_VALUE
        for (button in FvToolbarButton.entries) {
            val (cx, cy) = layout.toolbarButtonCenters.getOrNull(button.ordinal) ?: continue
            val dx = rawX - cx
            val dy = rawY - cy
            val distSq = dx * dx + dy * dy
            if (distSq <= hitRadius * hitRadius && distSq < bestDistSq) {
                bestDistSq = distSq
                bestButton = button
            }
        }
        return bestButton
    }

    fun isNearToolbar(layout: FvPanelLayout, rawX: Float, rawY: Float): Boolean {
        if (toolbarButtonAt(layout, rawX, rawY) != null) return true
        val hitRadius = toolbarHitRadius(layout)
        val centers = layout.toolbarButtonCenters
        if (centers.isEmpty()) return false
        val minY = centers.minOf { it.second } - hitRadius
        val maxY = centers.maxOf { it.second } + hitRadius
        val toolbarX = layout.toolbarCenterX
        val horizontalSlop = hitRadius * 1.2f
        return rawY in minY..maxY && kotlin.math.abs(rawX - toolbarX) <= horizontalSlop
    }

    private fun toolbarHitRadius(layout: FvPanelLayout): Float =
        layout.toolbarButtonRadiusPx * TOOLBAR_HIT_SCALE

    fun isOutsidePanel(layout: FvPanelLayout, rawX: Float, rawY: Float, extraMarginPx: Float): Boolean {
        if (toolbarButtonAt(layout, rawX, rawY) != null) return false
        val outerRadius = outerRadiusPx(layout.slots.size, layout.itemSizePx) + extraMarginPx
        val dx = rawX - layout.anchorX
        val dy = rawY - layout.anchorY
        return dx * dx + dy * dy > outerRadius * outerRadius
    }

    fun slotCountForCircleCount(circleCount: Int): Int = when (circleCount.coerceIn(1, 4)) {
        1 -> 5
        2 -> 13
        3 -> 24
        else -> 38
    }

    private fun buildAllSlotGeometry(): List<SlotGeo> {
        val iconSize = ICON_SIZE_DP
        val endMargin = END_MARGIN_RAD
        val halfPi = PI / 2.0
        val result = ArrayList<SlotGeo>(38)

        fun addLayer(layerIndex: Int, radiusDp: Float, slotCount: Int, slotWidthRad: Double, gapRad: Double) {
            var angleMax = halfPi - endMargin
            var boundary = (angleMax - slotWidthRad) - (gapRad / 2.0)
            var centerAngle = angleMax - (slotWidthRad / 2.0)
            repeat(slotCount) { slotInLayer ->
                val offsetX = (radiusDp * cos(centerAngle)).toFloat()
                val offsetY = (radiusDp * sin(centerAngle)).toFloat()
                val angleMaxDeg = toAngleDeg(angleMax)
                val angleMinDeg = if (slotInLayer == slotCount - 1) {
                    toAngleDeg(boundary + (gapRad / 2.0))
                } else {
                    toAngleDeg(boundary)
                }
                result += SlotGeo(offsetX, offsetY, angleMaxDeg, angleMinDeg, layerIndex)
                val nextBoundary = (boundary - slotWidthRad) - gapRad
                centerAngle = (boundary - (slotWidthRad / 2.0)) - (gapRad / 2.0)
                angleMax = boundary
                boundary = nextBoundary
            }
        }

        LAYER_RADIUS_DP.forEachIndexed { layerIndex, radiusDp ->
            val slotCount = LAYER_SLOT_COUNTS[layerIndex]
            val slotWidthRad = asin((iconSize * 0.5f) / radiusDp) * 2.0
            val gapRad = ((PI - (endMargin * 2.0)) - (slotWidthRad * slotCount)) / (slotCount - 1)
            addLayer(layerIndex, radiusDp, slotCount, slotWidthRad, gapRad)
        }
        return result
    }

    private fun radialBandSq(layerIndex: Int, density: Float): Pair<Int, Int> {
        val iconPx = (ICON_SIZE_DP * density).toInt()
        val r0 = (LAYER_RADIUS_DP[0] * density).toInt()
        val r1 = (LAYER_RADIUS_DP[1] * density).toInt()
        val r2 = (LAYER_RADIUS_DP[2] * density).toInt()
        val r3 = (LAYER_RADIUS_DP[3] * density).toInt()
        return when (layerIndex) {
            0 -> {
                val min = ((r0 - iconPx * 1.5f) * (r0 - iconPx * 1.5f)).toInt()
                val mid = (((r1 - r0) / 2) + r0)
                val max = mid * mid
                min to max
            }
            1 -> {
                val mid = (((r1 - r0) / 2) + r0)
                val min = mid * mid
                val nextMid = (((r2 - r1) / 2) + r1)
                val max = nextMid * nextMid
                min to max
            }
            2 -> {
                val mid = (((r2 - r1) / 2) + r1)
                val min = mid * mid
                val nextMid = (((r3 - r2) / 2) + r2)
                val max = nextMid * nextMid
                min to max
            }
            else -> {
                val mid = (((r3 - r2) / 2) + r2)
                val min = mid * mid
                val max = ((iconPx * 2) + r3) * ((iconPx * 2) + r3)
                min to max
            }
        }
    }

    private fun buildToolbar(
        side: FvAppSwitcherSide,
        anchorY: Float,
        screenWidth: Float,
        density: Float,
        toolbarRadiusPx: Float,
    ): Pair<Float, List<Pair<Float, Float>>> {
        val gapPx = TOOLBAR_GAP_DP * density
        val edgeGapPx = TOOLBAR_EDGE_GAP_DP * density
        val buttonDiameterPx = toolbarRadiusPx * 2f
        // FV CircleAppContainer：左贴边→工具列 gravity=TOP|RIGHT；右贴边→TOP|LEFT（对侧贴边）
        val toolbarX = when (side) {
            FvAppSwitcherSide.LEFT -> screenWidth - edgeGapPx - toolbarRadiusPx
            FvAppSwitcherSide.RIGHT -> edgeGapPx + toolbarRadiusPx
        }
        val totalHeight = (TOOLBAR_BUTTON_COUNT * buttonDiameterPx) + ((TOOLBAR_BUTTON_COUNT - 1) * gapPx)
        val topY = anchorY - totalHeight / 2f + toolbarRadiusPx
        val centers = List(TOOLBAR_BUTTON_COUNT) { index ->
            val y = topY + index * (buttonDiameterPx + gapPx)
            toolbarX to y
        }
        return toolbarX to centers
    }

    private fun toScreenOffset(
        offsetX: Float,
        offsetY: Float,
        side: FvAppSwitcherSide,
        anchorX: Float,
        anchorY: Float,
        density: Float,
    ): Pair<Float, Float> {
        val scaledX = offsetX * density
        val scaledY = offsetY * density
        val mirroredX = if (side == FvAppSwitcherSide.LEFT) scaledX else -scaledX
        return anchorX + mirroredX to anchorY + scaledY
    }

    private fun relativeX(layout: FvPanelLayout, rawX: Float): Float =
        if (layout.side == FvAppSwitcherSide.LEFT) {
            rawX - layout.anchorX
        } else {
            layout.anchorX - rawX
        }

    private fun toAngleDeg(radians: Double): Int = (radians * 180.0 / PI).toInt()

    private fun outerRadiusPx(slotCount: Int, itemSizePx: Float): Float {
        val density = itemSizePx / ICON_SIZE_DP
        val layerIndex = when {
            slotCount <= 5 -> 0
            slotCount <= 13 -> 1
            slotCount <= 24 -> 2
            else -> 3
        }
        return LAYER_RADIUS_DP[layerIndex] * density + itemSizePx
    }
}

enum class FvToolbarButton {
    HIDE,
    MOVE,
    PIN,
    SETTINGS,
    KEYBOARD,
}
