package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.slideindex.app.gesture.SwipeDirection
import com.slideindex.app.gesture.actionFor
import com.slideindex.app.gesture.isEffective
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.GestureHintStyle
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Edge-aligned gesture hint renderer with fluid physics, spring tension, and organic morphing.
 */
object GestureHintRenderer {
    data class HintTarget(
        val direction: SwipeDirection,
        val centerAngleDegrees: Float,
    )

    private object Params {
        const val WAVE_WIDTH_DP = 40f
        const val WAVE_BEZIER_HALF_RATIO = 2.5f
        const val CAPSULE_THICKNESS_DP = 36f
        const val CAPSULE_MAX_LENGTH_DP = 72f
        const val CAPSULE_CORNER_RADIUS_DP = 18f
        const val BUBBLE_DIAMETER_DP = 44f
        const val BUBBLE_MAX_OFFSET_DP = 82f
        const val PIXEL_HEIGHT_DP = 52f
        const val PIXEL_MIN_WIDTH_DP = 34f
        const val PIXEL_MAX_WIDTH_DP = 74f
        const val ICON_SCALE = 0.52f
    }

    private data class HintColors(
        val fill: Int,
        val iconBadge: Int,
        val icon: Int,
    )

    fun configuredTargets(
        side: PanelSide,
        settings: AppSettings,
        handleId: String = com.slideindex.app.gesture.TriggerHandle.DEFAULT_ID,
    ): List<HintTarget> {
        val config = settings.gestureAngleConfig.normalized()
        return config.sectorCenterAngles().filter { (direction, _) ->
            direction.isConfigured(side, settings, handleId)
        }.map { (direction, angle) ->
            HintTarget(direction, angle)
        }
    }

    fun drawPreviewHints(
        canvas: Canvas,
        side: PanelSide,
        edgeX: Float,
        triggerZone: RectF,
        shortRadiusPx: Float,
        targets: List<HintTarget>,
        cyclePhase: Float,
        density: Float,
        style: GestureHintStyle,
        themeColor: Int,
    ) {
        if (targets.isEmpty() || triggerZone.height() <= 0f) return
        val activeIndex = (cyclePhase * targets.size)
            .toInt()
            .coerceIn(0, targets.lastIndex)
        val target = targets[activeIndex]
        val segment = 1f / targets.size
        val segmentStart = activeIndex * segment
        val localPhase = ((cyclePhase - segmentStart) / segment).coerceIn(0f, 1f)
        val rawInwardPx = shortRadiusPx * easeOut(localPhase)
        val edgeY = anchorYForAngle(triggerZone, target.centerAngleDegrees)

        drawEdgeAlignedHint(
            canvas = canvas,
            side = side,
            edgeX = edgeX,
            edgeY = edgeY,
            rawInwardPx = rawInwardPx,
            edgeOffsetPx = 0f,
            density = density,
            style = style,
            themeColor = themeColor,
            swipeDirection = target.direction,
            alpha = 235,
        )
    }

    fun drawLiveHint(
        canvas: Canvas,
        side: PanelSide,
        edgeX: Float,
        edgeY: Float,
        swipeDirection: SwipeDirection?,
        inwardPx: Float,
        edgeOffsetPx: Float,
        density: Float,
        style: GestureHintStyle,
        themeColor: Int,
    ) {
        if (inwardPx <= density) return
        drawEdgeAlignedHint(
            canvas = canvas,
            side = side,
            edgeX = edgeX,
            edgeY = edgeY,
            rawInwardPx = inwardPx,
            edgeOffsetPx = edgeOffsetPx,
            density = density,
            style = style,
            themeColor = themeColor,
            swipeDirection = swipeDirection,
            alpha = 235,
        )
    }

    fun drawStyleIcon(
        canvas: Canvas,
        style: GestureHintStyle,
        boxSizePx: Float,
        density: Float,
        themeColor: Int,
    ) {
        drawEdgeAlignedHint(
            canvas = canvas,
            side = PanelSide.LEFT,
            edgeX = 0f,
            edgeY = boxSizePx * 0.5f,
            rawInwardPx = density * 24f,
            edgeOffsetPx = 0f,
            density = density,
            style = style,
            themeColor = themeColor,
            swipeDirection = SwipeDirection.IN,
            alpha = 240,
        )
    }

    private fun drawEdgeAlignedHint(
        canvas: Canvas,
        side: PanelSide,
        edgeX: Float,
        edgeY: Float,
        rawInwardPx: Float,
        edgeOffsetPx: Float,
        density: Float,
        style: GestureHintStyle,
        themeColor: Int,
        swipeDirection: SwipeDirection?,
        alpha: Int,
    ) {
        if (rawInwardPx <= 0f) return
        val colors = buildColors(themeColor, alpha)
        val iconRotation = calculateDynamicRotation(side, rawInwardPx, edgeOffsetPx, swipeDirection)

        canvas.save()
        canvas.translate(edgeX, edgeY)
        if (side == PanelSide.RIGHT) {
            canvas.scale(-1f, 1f)
        }

        when (style) {
            GestureHintStyle.WAVE -> drawWave(
                canvas = canvas,
                inwardPx = rawInwardPx,
                edgeOffsetPx = edgeOffsetPx,
                density = density,
                colors = colors,
                iconRotation = iconRotation,
            )
            GestureHintStyle.CAPSULE -> drawCapsule(
                canvas = canvas,
                inwardPx = rawInwardPx,
                edgeOffsetPx = edgeOffsetPx,
                density = density,
                colors = colors,
                iconRotation = iconRotation,
            )
            GestureHintStyle.BUBBLE -> drawBubble(
                canvas = canvas,
                inwardPx = rawInwardPx,
                edgeOffsetPx = edgeOffsetPx,
                density = density,
                colors = colors,
                iconRotation = iconRotation,
            )
            GestureHintStyle.PIXEL_BACK -> drawPixelBack(
                canvas = canvas,
                inwardPx = rawInwardPx,
                edgeOffsetPx = edgeOffsetPx,
                density = density,
                themeColor = themeColor,
                alpha = alpha,
                iconRotation = iconRotation,
            )
        }
        canvas.restore()
    }

    /**
     * Water wave effect with tension: as inwardPx increases, the base widens and apex smoothens.
     */
    private fun drawWave(
        canvas: Canvas,
        inwardPx: Float,
        edgeOffsetPx: Float,
        density: Float,
        colors: HintColors,
        iconRotation: Float,
    ) {
        val maxWidth = dp(density, Params.WAVE_WIDTH_DP)
        val progressPx = applySpringTension(inwardPx, maxWidth, 50f * density)
        if (progressPx <= 1f) return

        val tensionFactor = (progressPx / maxWidth).coerceIn(0f, 1f)
        val bezierLengthHalf = maxWidth * Params.WAVE_BEZIER_HALF_RATIO * (1f + tensionFactor * 0.4f)
        val transformLimit = bezierLengthHalf / 2f
        val transformOffset = edgeOffsetPx.coerceIn(-transformLimit, transformLimit)
        val safeOrigin = -transformOffset
        val safeFingerX = progressPx.coerceIn(1f, maxWidth)
        val edgePad = density

        val path = Path().apply {
            moveTo(0f, safeOrigin - bezierLengthHalf)
            val controlDist = bezierLengthHalf / 2.5f
            val midY1 = safeOrigin - controlDist - transformOffset
            cubicTo(-edgePad, midY1, safeFingerX, midY1 + controlDist * 0.5f * tensionFactor, safeFingerX, safeOrigin - transformOffset * 0.5f)
            val midY2 = safeOrigin + controlDist - transformOffset
            cubicTo(safeFingerX, midY2 - controlDist * 0.5f * tensionFactor, 0f, midY2, 0f, safeOrigin + bezierLengthHalf)
        }

        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colors.fill
        })

        val bounds = RectF()
        path.computeBounds(bounds, true)

        val parallaxX = bounds.left + bounds.width() * (0.42f + tensionFactor * 0.05f)
        val scaleBump = if (tensionFactor > 0.9f) 1.08f else 1f
        drawDirectionIcon(
            canvas = canvas,
            centerX = parallaxX,
            centerY = bounds.centerY() - transformOffset * 0.2f, // Parallax Y
            size = minOf(bounds.width(), bounds.height()) * Params.ICON_SCALE * scaleBump,
            rotation = iconRotation,
            colors = colors,
        )
    }

    /**
     * Tear-drop morphing: stretches organically from the base.
     */
    private fun drawCapsule(
        canvas: Canvas,
        inwardPx: Float,
        edgeOffsetPx: Float,
        density: Float,
        colors: HintColors,
        iconRotation: Float,
    ) {
        val thickness = dp(density, Params.CAPSULE_THICKNESS_DP)
        val maxLength = dp(density, Params.CAPSULE_MAX_LENGTH_DP)
        val progressPx = applySpringTension(inwardPx, maxLength, 80f * density).coerceAtLeast(thickness)
        
        val tensionFactor = ((progressPx - thickness) / (maxLength - thickness)).coerceIn(0f, 1f)
        val dynamicThickness = thickness * (1f - tensionFactor * 0.2f) // Narrows as it stretches
        val cornerRadius = dynamicThickness / 2f
        
        val centerShift = (progressPx / maxLength) * 0.2f
        val centerY = edgeOffsetPx * centerShift

        val path = Path().apply {
            val baseSpread = thickness * (1f + tensionFactor * 0.3f) / 2f
            moveTo(0f, centerY - baseSpread)
            
            // Top curve
            cubicTo(
                progressPx * 0.4f, centerY - baseSpread * 0.8f,
                progressPx - cornerRadius, centerY - cornerRadius,
                progressPx, centerY - cornerRadius
            )
            // Front cap
            arcTo(
                RectF(progressPx - dynamicThickness, centerY - cornerRadius, progressPx, centerY + cornerRadius),
                -90f, 180f, false
            )
            // Bottom curve
            cubicTo(
                progressPx - cornerRadius, centerY + cornerRadius,
                progressPx * 0.4f, centerY + baseSpread * 0.8f,
                0f, centerY + baseSpread
            )
            close()
        }

        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colors.fill
        })

        val scaleBump = if (tensionFactor > 0.9f) 1.1f else 1f
        drawDirectionIcon(
            canvas = canvas,
            centerX = progressPx - dynamicThickness / 2f,
            centerY = centerY,
            size = thickness * Params.ICON_SCALE * scaleBump,
            rotation = iconRotation,
            colors = colors,
        )
    }

    /**
     * Metaball logic: ball snaps out when threshold is reached.
     */
    private fun drawBubble(
        canvas: Canvas,
        inwardPx: Float,
        edgeOffsetPx: Float,
        density: Float,
        colors: HintColors,
        iconRotation: Float,
    ) {
        val diameter = dp(density, Params.BUBBLE_DIAMETER_DP)
        val radius = diameter / 2f
        val maxOffset = dp(density, Params.BUBBLE_MAX_OFFSET_DP).coerceAtLeast(radius)
        val progressPx = applySpringTension(inwardPx, maxOffset, 60f * density).coerceAtLeast(1f)
        
        val centerShift = (progressPx / maxOffset) * 0.18f
        val centerX = -radius + progressPx
        val centerY = edgeOffsetPx * centerShift

        val snapThreshold = diameter * 1.2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colors.fill
        }

        // Draw gooey connection if not fully snapped
        if (centerX - radius < snapThreshold * 0.4f) {
            val gooeyPath = Path().apply {
                val spread = diameter * (1f - (centerX / snapThreshold).coerceIn(0f, 1f))
                moveTo(0f, centerY - radius - spread)
                cubicTo(
                    centerX * 0.5f, centerY - radius * 0.8f,
                    centerX, centerY - radius,
                    centerX, centerY - radius
                )
                lineTo(centerX, centerY + radius)
                cubicTo(
                    centerX * 0.5f, centerY + radius * 0.8f,
                    0f, centerY + radius + spread,
                    0f, centerY + radius + spread
                )
            }
            canvas.drawPath(gooeyPath, paint)
        }

        canvas.drawCircle(centerX, centerY, radius, paint)

        val tensionFactor = (progressPx / maxOffset).coerceIn(0f, 1f)
        val scaleBump = if (tensionFactor > 0.85f) 1.15f else 1f
        drawDirectionIcon(
            canvas = canvas,
            centerX = centerX,
            centerY = centerY,
            size = diameter * Params.ICON_SCALE * scaleBump,
            rotation = iconRotation,
            colors = colors,
        )
    }

    /**
     * Native Material You jelly pill: Y-axis skew, damped horizontal stretch, and parallax arrow.
     */
    private fun drawPixelBack(
        canvas: Canvas,
        inwardPx: Float,
        edgeOffsetPx: Float,
        density: Float,
        themeColor: Int,
        alpha: Int,
        iconRotation: Float,
    ) {
        val height = dp(density, Params.PIXEL_HEIGHT_DP)
        val minWidth = dp(density, Params.PIXEL_MIN_WIDTH_DP)
        val maxWidth = dp(density, Params.PIXEL_MAX_WIDTH_DP)
        
        // Fluid spring tension
        val progressPx = applySpringTension(inwardPx, maxWidth, 90f * density).coerceAtLeast(1f)
        val width = progressPx.coerceAtLeast(minWidth)
        val halfHeight = height / 2f
        
        val tensionFactor = ((width - minWidth) / (maxWidth - minWidth)).coerceIn(0f, 1f)
        
        // Squeeze slightly when stretching (volume conservation)
        val dynamicHeight = height * (1f - tensionFactor * 0.08f)
        val dynamicHalfHeight = dynamicHeight / 2f
        
        // Y-axis drag bend (Skew effect)
        val bendY = (edgeOffsetPx * 0.15f * tensionFactor).coerceIn(-halfHeight * 0.5f, halfHeight * 0.5f)

        canvas.save()
        // Apply jelly bend
        canvas.skew(0f, bendY / width)
        
        val rect = RectF(-dynamicHalfHeight, -dynamicHalfHeight, width, dynamicHalfHeight)
        canvas.drawRoundRect(rect, dynamicHalfHeight, dynamicHalfHeight, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = pixelContainerColor(themeColor, alpha)
        })
        canvas.restore()

        // Parallax arrow mapping
        val baseArrowCenterX = pixelArrowCenterX(width, minWidth, dynamicHeight)
        val parallaxArrowX = baseArrowCenterX + tensionFactor * dp(density, 6f)
        val scaleBump = if (tensionFactor > 0.9f) 1.15f else 1f

        canvas.save()
        // Arrow rotates slightly based on Y drag and dynamic tracking
        canvas.translate(parallaxArrowX, bendY * 0.5f)
        canvas.rotate(iconRotation * 0.4f) // Softer rotation for pixel back

        drawPixelBackArrow(
            canvas = canvas,
            centerX = 0f,
            centerY = 0f,
            size = dynamicHeight * 0.34f * scaleBump,
            color = Color.argb(alpha.coerceIn(0, 255), 255, 255, 255),
        )
        canvas.restore()
    }

    fun drawPixelBackSnapshot(
        canvas: Canvas,
        side: PanelSide,
        edgeX: Float,
        snapshot: PixelBackGestureAnimationState.Snapshot,
        density: Float,
        themeColor: Int,
    ) {
        if (!snapshot.isVisible) return
        val height = dp(density, Params.PIXEL_HEIGHT_DP)
        val halfHeight = height / 2f
        val alpha = (snapshot.alpha * 255f).toInt().coerceIn(0, 255)

        canvas.save()
        canvas.translate(edgeX, snapshot.centerY)
        if (side == PanelSide.RIGHT) {
            canvas.scale(-1f, 1f)
        }
        val width = snapshot.widthPx.coerceAtLeast(0f)
        val rect = RectF(-halfHeight, -halfHeight, width, halfHeight)
        canvas.drawRoundRect(rect, halfHeight, halfHeight, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = pixelContainerColor(themeColor, alpha)
        })
        val arrowX = snapshot.arrowCenterOffsetPx + snapshot.arrowReleaseOffsetPx
        drawPixelBackArrow(
            canvas = canvas,
            centerX = arrowX,
            centerY = 0f,
            size = height * 0.34f,
            color = Color.argb(alpha, 255, 255, 255),
        )
        canvas.restore()
    }

    private fun pixelArrowCenterX(width: Float, minWidth: Float, height: Float): Float {
        val lowerBound = height * 0.38f
        val upperBound = maxOf(lowerBound, width - height * 0.24f)
        return (width * 0.58f + (width - minWidth).coerceAtLeast(0f) * 0.18f)
            .coerceIn(lowerBound, upperBound)
    }

    private fun drawPixelBackArrow(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float,
        color: Int,
    ) {
        val half = size / 2f
        val tipX = centerX - half
        val tailX = centerX + half * 0.55f
        canvas.drawPath(
            Path().apply {
                moveTo(tailX, centerY - half)
                lineTo(tipX, centerY)
                lineTo(tailX, centerY + half)
            },
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (size * 0.22f).coerceAtLeast(1.5f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                this.color = color
            },
        )
    }

    private fun drawDirectionIcon(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float,
        rotation: Float,
        colors: HintColors,
    ) {
        val badgeRadius = size * 0.62f
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(rotation)

        canvas.drawCircle(0f, 0f, badgeRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colors.iconBadge
        })

        val arm = badgeRadius * 0.58f
        val back = badgeRadius * 0.16f
        canvas.drawPath(
            Path().apply {
                moveTo(-back, -arm)
                lineTo(arm * 0.55f, 0f)
                lineTo(-back, arm)
            },
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (size * 0.11f).coerceAtLeast(1.6f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = colors.icon
            },
        )
        canvas.restore()
    }

    /**
     * Calculates smooth dynamic rotation for the icon based on finger drag offset.
     * Softly interpolates towards the target direction.
     */
    private fun calculateDynamicRotation(
        side: PanelSide,
        inwardPx: Float,
        edgeOffsetPx: Float,
        direction: SwipeDirection?
    ): Float {
        // Base tracking rotation towards the finger's vertical offset
        val trackAngle = Math.toDegrees(atan2(edgeOffsetPx.toDouble(), inwardPx.coerceAtLeast(1f).toDouble())).toFloat()
        
        val targetRotation = when (direction) {
            SwipeDirection.UP, SwipeDirection.UP_RIGHT -> if (side == PanelSide.LEFT) -45f else 45f
            SwipeDirection.IN -> 0f
            SwipeDirection.DOWN, SwipeDirection.DOWN_RIGHT -> if (side == PanelSide.LEFT) 45f else -45f
            null -> 0f
        }
        
        // Blend tracking angle with target sector angle based on pull distance
        val blendFactor = (inwardPx / 150f).coerceIn(0f, 1f)
        val blended = trackAngle * (1f - blendFactor) + targetRotation * blendFactor
        
        return blended
    }

    /**
     * Spring tension mathematical model.
     * Prevents linear scaling and creates a viscous pulling sensation.
     */
    private fun applySpringTension(inwardPx: Float, capPx: Float, tensionConstant: Float): Float {
        if (inwardPx <= 0f) return 0f
        return capPx * (1f - exp(-inwardPx / tensionConstant))
    }

    private fun anchorYForAngle(zone: RectF, angleDegrees: Float): Float {
        val t = ((90f - angleDegrees) / 180f).coerceIn(0f, 1f)
        return zone.top + zone.height() * t
    }

    private fun pixelContainerColor(themeColor: Int, alpha: Int): Int {
        val r = blend(Color.red(themeColor), 28, 0.72f)
        val g = blend(Color.green(themeColor), 27, 0.76f)
        val b = blend(Color.blue(themeColor), 46, 0.68f)
        return Color.argb(alpha.coerceIn(0, 255), r, g, b)
    }

    private fun buildColors(themeColor: Int, alpha: Int): HintColors {
        val softened = soften(themeColor)
        val r = Color.red(softened)
        val g = Color.green(softened)
        val b = Color.blue(softened)
        val fillAlpha = alpha.coerceIn(0, 255)
        return HintColors(
            fill = Color.argb(fillAlpha, r, g, b),
            iconBadge = Color.argb(
                (fillAlpha * 0.72f).toInt().coerceIn(0, 255),
                (r * 0.72f).toInt().coerceIn(0, 255),
                (g * 0.72f).toInt().coerceIn(0, 255),
                (b * 0.72f).toInt().coerceIn(0, 255),
            ),
            icon = Color.argb(fillAlpha, 255, 255, 255),
        )
    }

    private fun soften(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.rgb(
            blend(r, 176, 0.34f),
            blend(g, 136, 0.34f),
            blend(b, 220, 0.28f),
        )
    }

    private fun blend(from: Int, to: Int, fraction: Float): Int =
        (from + (to - from) * fraction).toInt().coerceIn(0, 255)

    private fun dp(density: Float, value: Float): Float = density * value

    private fun easeOut(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        val inv = 1f - t
        return 1f - inv * inv * inv
    }

    private fun SwipeDirection.isConfigured(
        side: PanelSide,
        settings: AppSettings,
        handleId: String = com.slideindex.app.gesture.TriggerHandle.DEFAULT_ID,
    ): Boolean {
        val short = toTrigger(long = false)
        val long = toTrigger(long = true)
        return settings.actionFor(side, short, handleId).isEffective() ||
            settings.actionFor(side, long, handleId).isEffective()
    }
}
