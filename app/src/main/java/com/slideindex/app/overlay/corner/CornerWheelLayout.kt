package com.slideindex.app.overlay.corner

import androidx.compose.ui.geometry.Offset
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.CornerRadialMenuCodec
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal object CornerWheelLayout {
    private const val ARC_DEGREES = 90f
    private const val EDGE_PADDING_DEG = 10f
    fun bubbleRadiusPx(settings: CornerGestureSettings, density: Float): Float =
        settings.bubbleSizeDp * density

    private val RING_CENTER_FRACTION = floatArrayOf(1f / 6f, 3f / 6f, 5f / 6f)

    fun editButtonCenter(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        settings: CornerGestureSettings,
        density: Float,
    ): Offset {
        val topOuterSlotIndex = CornerRadialMenuCodec.layerStartIndex(2)
        val topSlot = bubbleCenterForSlot(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            globalIndex = topOuterSlotIndex,
            settings = settings,
            density = density,
        )
        val visualOuter = layerRadiusPx(settings, density, 2) + bubbleRadiusPx(settings, density)
        val editR = editButtonRadius(density)
        val centerX = when (anchor) {
            CornerAnchor.LEFT -> anchorX + visualOuter + editR * 0.42f
            CornerAnchor.RIGHT -> anchorX - visualOuter - editR * 0.42f
        }
        return Offset(centerX, topSlot.y)
    }

    fun layerRadiusPx(settings: CornerGestureSettings, density: Float, layer: Int): Float {
        val inner = settings.innerDiameterDp * density / 2f
        val outer = settings.outerDiameterDp * density / 2f
        val bubble = bubbleRadiusPx(settings, density)
        val minBand = bubble * 2.35f * 3.2f
        val band = (outer - inner).coerceAtLeast(minBand)
        return inner + band * RING_CENTER_FRACTION[layer.coerceIn(0, 2)]
    }

    /** 轮盘实际外缘（最外环气泡中心 + 余量），用于「轮盘外取消」判定。 */
    fun wheelOuterHitRadiusPx(settings: CornerGestureSettings, density: Float): Float =
        layerRadiusPx(settings, density, 2) + bubbleRadiusPx(settings, density) * 1.5f

    fun editButtonRadius(density: Float): Float = 24f * density

    fun isEditButtonHit(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        settings: CornerGestureSettings,
        fingerX: Float,
        fingerY: Float,
        density: Float,
    ): Boolean {
        val center = editButtonCenter(anchor, anchorX, anchorY, settings, density)
        val radius = editButtonRadius(density)
        return hypot(fingerX - center.x, fingerY - center.y) <= radius * 1.55f
    }

    fun activeLayerCount(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        fingerX: Float,
        fingerY: Float,
        settings: CornerGestureSettings,
        density: Float,
        progressive: Boolean,
    ): Int {
        if (!progressive) return 3
        val dist = hypot(fingerX - anchorX, fingerY - anchorY)
        val bubble = bubbleRadiusPx(settings, density)
        val innerR = layerRadiusPx(settings, density, 0)
        val middleR = layerRadiusPx(settings, density, 1)
        val slop = bubble * 0.35f
        return when {
            dist <= innerR + slop -> 1
            dist <= middleR + slop -> 2
            else -> 3
        }
    }

    fun bubbleCenterForSlot(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        globalIndex: Int,
        settings: CornerGestureSettings,
        density: Float,
    ): Offset {
        val layer = CornerRadialMenuCodec.layerOf(globalIndex)
        val localIndex = CornerRadialMenuCodec.layerLocalIndex(globalIndex)
        val slotCount = CornerRadialMenuCodec.slotCountInLayer(layer)
        val radius = layerRadiusPx(settings, density, layer)
        val usableArc = ARC_DEGREES - EDGE_PADDING_DEG * 2f
        val sweep = usableArc / slotCount
        val start = arcStartDegrees(anchor) + EDGE_PADDING_DEG
        val visualIndex = when (anchor) {
            CornerAnchor.LEFT -> localIndex
            CornerAnchor.RIGHT -> slotCount - 1 - localIndex
        }
        val angle = start + (visualIndex + 0.5f) * sweep
        return polarOffset(anchorX, anchorY, radius, angle)
    }

    fun isInInnerZone(
        anchorX: Float,
        anchorY: Float,
        fingerX: Float,
        fingerY: Float,
        settings: CornerGestureSettings,
        density: Float,
    ): Boolean {
        val inner = settings.innerDiameterDp * density / 2f
        return hypot(fingerX - anchorX, fingerY - anchorY) < inner
    }

    fun isOutsideWheel(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        fingerX: Float,
        fingerY: Float,
        screenWidth: Float,
        screenHeight: Float,
        settings: CornerGestureSettings,
        density: Float,
    ): Boolean {
        if (isEditButtonHit(anchor, anchorX, anchorY, settings, fingerX, fingerY, density)) {
            return false
        }
        val dist = hypot(fingerX - anchorX, fingerY - anchorY)
        if (dist > wheelOuterHitRadiusPx(settings, density)) return true
        var angle = Math.toDegrees(
            kotlin.math.atan2(
                (fingerY - anchorY).toDouble(),
                (fingerX - anchorX).toDouble(),
            ),
        ).toFloat()
        if (angle < 0f) angle += 360f
        return !isAngleInArc(anchor, angle)
    }

    private fun arcStartDegrees(anchor: CornerAnchor): Float = when (anchor) {
        CornerAnchor.LEFT -> 270f
        CornerAnchor.RIGHT -> 180f
    }

    private fun isAngleInArc(anchor: CornerAnchor, angle: Float): Boolean = when (anchor) {
        CornerAnchor.LEFT -> angle >= 270f - EDGE_PADDING_DEG || angle <= EDGE_PADDING_DEG
        CornerAnchor.RIGHT -> angle in (180f - EDGE_PADDING_DEG)..(270f + EDGE_PADDING_DEG)
    }

    private fun polarOffset(cx: Float, cy: Float, radius: Float, angleDegrees: Float): Offset {
        val radians = Math.toRadians(angleDegrees.toDouble())
        return Offset(
            x = cx + (cos(radians) * radius).toFloat(),
            y = cy + (sin(radians) * radius).toFloat(),
        )
    }
}
