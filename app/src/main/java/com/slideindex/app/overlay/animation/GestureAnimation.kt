package com.slideindex.app.overlay.animation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.animation.GestureAnimationTriggerDirection.Center
import com.slideindex.app.overlay.animation.GestureAnimationTriggerDirection.Center2
import com.slideindex.app.overlay.animation.GestureAnimationTriggerDirection.Click
import com.slideindex.app.overlay.animation.GestureAnimationTriggerDirection.Down
import com.slideindex.app.overlay.animation.GestureAnimationTriggerDirection.Down2
import com.slideindex.app.overlay.animation.GestureAnimationTriggerDirection.Up
import com.slideindex.app.overlay.animation.GestureAnimationTriggerDirection.Up2
import com.slideindex.app.settings.AnimationStyle
import com.slideindex.app.settings.BubbleStyle
import com.slideindex.app.settings.CapsuleStyle
import com.slideindex.app.settings.WaveStyle
import com.slideindex.app.settings.animationIconInitialRotation
import com.slideindex.app.settings.painterIcon

@Composable
fun GestureAnimation(
    animationStyle: AnimationStyle,
    animationState: GestureAnimationState,
    modifier: Modifier = Modifier,
) {
    when (animationStyle) {
        is WaveStyle -> WaveGestureAnimation(
            modifier = modifier,
            animationStyle = animationStyle,
            animationState = animationState,
        )
        is CapsuleStyle -> CapsuleGestureAnimation(
            modifier = modifier,
            animationStyle = animationStyle,
            animationState = animationState,
        )
        is BubbleStyle -> BubbleGestureAnimation(
            modifier = modifier,
            animationStyle = animationStyle,
            animationState = animationState,
        )
    }
}

@Composable
private fun CapsuleGestureAnimation(
    animationStyle: CapsuleStyle,
    animationState: GestureAnimationState,
    modifier: Modifier = Modifier,
) {
    val button = animationState.button ?: return
    val icon = animationStyle.painterIcon()

    Canvas(modifier = modifier) {
        val originXAnimVal = animationState.originXAnimVal
        val originYAnimVal = animationState.originYAnimVal
        val fingerXAnimVal = animationState.fingerXAnimVal
        val fingerYAnimVal = animationState.fingerYAnimVal
        if (originXAnimVal.isNaN() ||
            originYAnimVal.isNaN() ||
            fingerXAnimVal.isNaN() ||
            fingerYAnimVal.isNaN()
        ) {
            return@Canvas
        }

        val progress = when (button.position) {
            GestureAnimationPosition.Left -> fingerXAnimVal
            GestureAnimationPosition.Right -> -fingerXAnimVal
            GestureAnimationPosition.Bottom -> -fingerYAnimVal
        }.coerceAtLeast(0f)
        if (progress <= 1f) {
            return@Canvas
        }

        val thickness = animationStyle.thickness.toFloat().coerceAtLeast(1f)
        val strokeWidth = animationStyle.strokeWidth.toFloat()
        val maxLength = animationStyle.maxLength.toFloat().coerceAtLeast(thickness)
        val length = progress.coerceAtMost(maxLength).coerceAtLeast(thickness)
        val entryDistance = (thickness + strokeWidth * 2f).coerceAtLeast(1f)
        val entryProgress = (progress / entryDistance).coerceIn(0f, 1f)
        val centerShiftRatio = (progress / maxLength).coerceIn(0f, 1f) * 0.2f
        val centerX = when (button.position) {
            GestureAnimationPosition.Left, GestureAnimationPosition.Right -> 0f
            GestureAnimationPosition.Bottom ->
                (originXAnimVal + (fingerXAnimVal - originXAnimVal) * centerShiftRatio)
                    .coerceIn(thickness / 2f, size.width - thickness / 2f)
        }
        val centerY = when (button.position) {
            GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
                (originYAnimVal + (fingerYAnimVal - originYAnimVal) * centerShiftRatio)
                    .coerceIn(thickness / 2f, size.height - thickness / 2f)
            GestureAnimationPosition.Bottom -> 0f
        }
        val leftStart = lerpFloat(start = -length - strokeWidth, stop = 0f, fraction = entryProgress)
        val rightStart = lerpFloat(
            start = size.width + strokeWidth,
            stop = size.width - length,
            fraction = entryProgress,
        )
        val bottomStart = lerpFloat(
            start = size.height + strokeWidth,
            stop = size.height - length,
            fraction = entryProgress,
        )
        val topLeft = when (button.position) {
            GestureAnimationPosition.Left -> Offset(leftStart, centerY - thickness / 2f)
            GestureAnimationPosition.Right -> Offset(rightStart, centerY - thickness / 2f)
            GestureAnimationPosition.Bottom -> Offset(centerX - thickness / 2f, bottomStart)
        }
        val rectSize = when (button.position) {
            GestureAnimationPosition.Left, GestureAnimationPosition.Right -> Size(length, thickness)
            GestureAnimationPosition.Bottom -> Size(thickness, length)
        }
        val radiusCap = minOf(rectSize.width, rectSize.height) / 2f
        val cornerRadius = animationStyle.cornerRadius.toFloat().coerceIn(0f, radiusCap)
        val activeAlpha = if (animationState.canDistanceTriggered(button, false)) 1f else 0.55f

        drawRoundRect(
            color = Color(animationStyle.backgroundColor),
            topLeft = topLeft,
            size = rectSize,
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        )
        if (animationStyle.strokeWidth > 0) {
            drawRoundRect(
                color = Color(animationStyle.strokeColor),
                topLeft = topLeft,
                size = rectSize,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(animationStyle.strokeWidth.toFloat()),
            )
        }

        val degree = animationIconInitialRotation(button.position) +
            triggerRotationOffset(animationState.triggerDirection, button.position)
        val iconSize = minOf(rectSize.width, rectSize.height) * animationStyle.iconScale
        val rectCenter = Offset(
            x = topLeft.x + rectSize.width / 2f,
            y = topLeft.y + rectSize.height / 2f,
        )
        rotate(degree, pivot = rectCenter) {
            translate(left = rectCenter.x - iconSize / 2f, top = rectCenter.y - iconSize / 2f) {
                icon.run {
                    draw(
                        size = Size(iconSize, iconSize),
                        colorFilter = ColorFilter.tint(Color(animationStyle.iconColor)),
                        alpha = activeAlpha,
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleGestureAnimation(
    animationStyle: BubbleStyle,
    animationState: GestureAnimationState,
    modifier: Modifier = Modifier,
) {
    val button = animationState.button ?: return
    val icon = animationStyle.painterIcon()

    Canvas(modifier = modifier) {
        val originXAnimVal = animationState.originXAnimVal
        val originYAnimVal = animationState.originYAnimVal
        val fingerXAnimVal = animationState.fingerXAnimVal
        val fingerYAnimVal = animationState.fingerYAnimVal
        if (originXAnimVal.isNaN() ||
            originYAnimVal.isNaN() ||
            fingerXAnimVal.isNaN() ||
            fingerYAnimVal.isNaN()
        ) {
            return@Canvas
        }

        val progress = when (button.position) {
            GestureAnimationPosition.Left -> fingerXAnimVal
            GestureAnimationPosition.Right -> -fingerXAnimVal
            GestureAnimationPosition.Bottom -> -fingerYAnimVal
        }.coerceAtLeast(0f)
        if (progress <= 1f) {
            return@Canvas
        }

        val diameter = animationStyle.diameter.toFloat().coerceAtLeast(1f)
        val radius = diameter / 2f
        val strokeWidth = animationStyle.strokeWidth.toFloat()
        val offset = progress.coerceAtMost(animationStyle.maxOffset.toFloat().coerceAtLeast(radius))
        val centerShiftRatio = (progress / animationStyle.maxOffset.toFloat().coerceAtLeast(1f))
            .coerceIn(0f, 1f) * 0.18f
        val centerX = when (button.position) {
            GestureAnimationPosition.Left -> -radius + offset
            GestureAnimationPosition.Right -> size.width + radius - offset
            GestureAnimationPosition.Bottom ->
                (originXAnimVal + (fingerXAnimVal - originXAnimVal) * centerShiftRatio)
                    .coerceIn(radius, size.width - radius)
        }
        val centerY = when (button.position) {
            GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
                (originYAnimVal + (fingerYAnimVal - originYAnimVal) * centerShiftRatio)
                    .coerceIn(radius, size.height - radius)
            GestureAnimationPosition.Bottom -> size.height + radius - offset
        }
        val activeAlpha = if (animationState.canDistanceTriggered(button, false)) 1f else 0.55f

        drawCircle(
            color = Color(animationStyle.backgroundColor),
            radius = radius,
            center = Offset(centerX, centerY),
        )
        if (animationStyle.strokeWidth > 0) {
            drawCircle(
                color = Color(animationStyle.strokeColor),
                radius = radius - strokeWidth / 2f,
                center = Offset(centerX, centerY),
                style = Stroke(width = strokeWidth),
            )
        }

        val degree = animationIconInitialRotation(button.position) +
            triggerRotationOffset(animationState.triggerDirection, button.position)
        val iconSize = diameter * animationStyle.iconScale
        val bubbleCenter = Offset(centerX, centerY)
        rotate(degree, pivot = bubbleCenter) {
            translate(
                left = centerX - radius + radius - iconSize / 2f,
                top = centerY - radius + radius - iconSize / 2f,
            ) {
                icon.run {
                    draw(
                        size = Size(iconSize, iconSize),
                        colorFilter = ColorFilter.tint(Color(animationStyle.iconColor)),
                        alpha = activeAlpha,
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveGestureAnimation(
    animationStyle: WaveStyle,
    animationState: GestureAnimationState,
    modifier: Modifier = Modifier,
) {
    val button = animationState.button ?: return
    val icon = animationStyle.painterIcon()
    val bezierPath = remember { Path() }
    val density = LocalDensity.current
    val bezierOffset = when (button.position) {
        GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
            if (animationStyle.safeBounds) with(density) { 70.dp.toPx() } else 0f
        GestureAnimationPosition.Bottom -> 0f
    }
    val bezierSpacing = if (animationStyle.safeBounds) with(density) { 40.dp.toPx() } else 0f
    val bezierMaxWidth = animationStyle.width.toFloat()
    val bezierLengthHalf = bezierMaxWidth * animationStyle.bezierLengthHalfRatio
    val bezierTransformOffsetCoerce = if (animationStyle.transformEnabled) bezierLengthHalf / 2f else 0f

    Canvas(modifier = modifier) {
        val triggerDirection = animationState.triggerDirection
        val originXAnimVal = animationState.originXAnimVal
        val originYAnimVal = animationState.originYAnimVal
        val fingerXAnimVal = animationState.fingerXAnimVal
        val fingerYAnimVal = animationState.fingerYAnimVal
        if (originXAnimVal.isNaN() ||
            originYAnimVal.isNaN() ||
            fingerXAnimVal.isNaN() ||
            fingerYAnimVal.isNaN()
        ) {
            return@Canvas
        }
        when (button.position) {
            GestureAnimationPosition.Left -> if (fingerXAnimVal < 0f) return@Canvas
            GestureAnimationPosition.Right -> if (fingerXAnimVal > 0f) return@Canvas
            GestureAnimationPosition.Bottom -> if (fingerYAnimVal > 0f) return@Canvas
        }

        val transformOffset = when (button.position) {
            GestureAnimationPosition.Left, GestureAnimationPosition.Right -> originYAnimVal - fingerYAnimVal
            GestureAnimationPosition.Bottom -> originXAnimVal - fingerXAnimVal
        }.coerceIn(-bezierTransformOffsetCoerce, bezierTransformOffsetCoerce)

        val safeOrigin = when (button.position) {
            GestureAnimationPosition.Left, GestureAnimationPosition.Right -> originYAnimVal - bezierOffset
            GestureAnimationPosition.Bottom -> originXAnimVal - bezierOffset
        }.coerceIn(
            minimumValue = when (animationStyle.safeBounds) {
                true -> bezierLengthHalf + bezierSpacing
                else -> 0f
            },
            maximumValue = when (button.position) {
                GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
                    when (animationStyle.safeBounds) {
                        true -> size.height - bezierLengthHalf - bezierSpacing
                        else -> size.height
                    }
                GestureAnimationPosition.Bottom ->
                    when (animationStyle.safeBounds) {
                        true -> size.width - bezierLengthHalf - bezierSpacing
                        else -> size.width
                    }
            },
        )

        bezierPath.also { path ->
            path.reset()
            val moveToX = when (button.position) {
                GestureAnimationPosition.Left -> 0f
                GestureAnimationPosition.Right -> size.width
                GestureAnimationPosition.Bottom -> safeOrigin - bezierLengthHalf
            }
            val moveToY = when (button.position) {
                GestureAnimationPosition.Left, GestureAnimationPosition.Right -> safeOrigin - bezierLengthHalf
                GestureAnimationPosition.Bottom -> size.height
            }
            path.moveTo(moveToX, moveToY)

            val factor = 1.dp.toPx()
            when (button.position) {
                GestureAnimationPosition.Left, GestureAnimationPosition.Right -> {
                    val safeFingerX = when (button.position) {
                        GestureAnimationPosition.Left -> fingerXAnimVal.coerceAtMost(bezierMaxWidth)
                        else -> (size.width + fingerXAnimVal).coerceAtLeast(size.width - bezierMaxWidth)
                    }
                    var safeFingerY = safeOrigin - bezierLengthHalf / 2.5f - transformOffset
                    path.cubicTo(
                        x1 = when (button.position) {
                            GestureAnimationPosition.Left -> -factor
                            else -> size.width + factor
                        },
                        y1 = safeFingerY,
                        x2 = safeFingerX,
                        y2 = safeFingerY,
                        x3 = safeFingerX,
                        y3 = safeOrigin - transformOffset,
                    )
                    safeFingerY = safeOrigin + bezierLengthHalf / 2.5f - transformOffset
                    path.cubicTo(
                        x1 = safeFingerX,
                        y1 = safeFingerY,
                        x2 = when (button.position) {
                            GestureAnimationPosition.Left -> 0f
                            else -> size.width
                        },
                        y2 = safeFingerY,
                        x3 = when (button.position) {
                            GestureAnimationPosition.Left -> -factor
                            else -> size.width + factor
                        },
                        y3 = safeOrigin + bezierLengthHalf,
                    )
                }
                GestureAnimationPosition.Bottom -> {
                    var safeFingerX = safeOrigin - bezierLengthHalf / 2.5f - transformOffset
                    val safeFingerY = (size.height + fingerYAnimVal).coerceAtLeast(size.height - bezierMaxWidth)
                    path.cubicTo(
                        x1 = safeFingerX,
                        y1 = size.height + factor,
                        x2 = safeFingerX,
                        y2 = safeFingerY,
                        x3 = safeOrigin - transformOffset,
                        y3 = safeFingerY,
                    )
                    safeFingerX = safeOrigin + bezierLengthHalf / 2.5f - transformOffset
                    path.cubicTo(
                        x1 = safeFingerX,
                        y1 = safeFingerY,
                        x2 = safeFingerX,
                        y2 = size.height,
                        x3 = safeOrigin + bezierLengthHalf,
                        y3 = size.height + factor,
                    )
                }
            }

            if (animationStyle.strokeWidth > 0) {
                val offset2 = when (button.position) {
                    GestureAnimationPosition.Left -> Offset(-animationStyle.strokeWidth.toFloat(), 0f)
                    GestureAnimationPosition.Right -> Offset(animationStyle.strokeWidth.toFloat(), 0f)
                    GestureAnimationPosition.Bottom -> Offset(0f, animationStyle.strokeWidth.toFloat())
                }
                path.translate(offset2)
            }
        }

        drawPath(path = bezierPath, color = Color(animationStyle.backgroundColor))
        if (animationStyle.strokeWidth > 0) {
            drawPath(
                path = bezierPath,
                color = Color(animationStyle.strokeColor),
                style = Stroke(animationStyle.strokeWidth.toFloat()),
            )
        }

        val bezierBounds = when (button.position) {
            GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
                bezierPath.getBounds().translate(Offset(0f, -transformOffset))
            GestureAnimationPosition.Bottom ->
                bezierPath.getBounds().translate(Offset(-transformOffset, 0f))
        }
        val initialDegree = animationIconInitialRotation(button.position)
        val degree = initialDegree + triggerRotationOffset(triggerDirection, button.position)
        rotate(degree, pivot = bezierBounds.center) {
            val radius = when (button.position) {
                GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
                    bezierBounds.width * animationStyle.iconScale
                GestureAnimationPosition.Bottom ->
                    bezierBounds.height * animationStyle.iconScale
            }
            val paddingHori = (bezierBounds.width - radius) / 2f
            val paddingVert = (bezierBounds.height - radius) / 2f
            val left = when (button.position) {
                GestureAnimationPosition.Left -> paddingHori - animationStyle.strokeWidth
                GestureAnimationPosition.Right ->
                    size.width - bezierBounds.width + paddingHori + animationStyle.strokeWidth
                GestureAnimationPosition.Bottom ->
                    bezierBounds.left + bezierBounds.width / 2f - radius / 2f
            }
            val top = when (button.position) {
                GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
                    bezierBounds.top + bezierBounds.height / 2f - radius / 2f
                GestureAnimationPosition.Bottom ->
                    size.height - bezierBounds.height + paddingVert + animationStyle.strokeWidth
            }
            translate(left = left, top = top) {
                val canTriggered = animationState.canDistanceTriggered(button, false)
                icon.run {
                    draw(
                        size = Size(radius, radius),
                        colorFilter = ColorFilter.tint(Color(animationStyle.iconColor)),
                        alpha = if (canTriggered) 1f else 0.25f,
                    )
                }
            }
        }
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private fun triggerRotationOffset(
    triggerDirection: GestureAnimationTriggerDirection,
    position: GestureAnimationPosition,
): Float = when (triggerDirection) {
    Up -> when (position) {
        GestureAnimationPosition.Left -> -45f
        GestureAnimationPosition.Right -> 45f
        GestureAnimationPosition.Bottom -> -45f
    }
    Center, Center2, Click -> 0f
    Down -> when (position) {
        GestureAnimationPosition.Left -> 45f
        GestureAnimationPosition.Right -> -45f
        GestureAnimationPosition.Bottom -> 45f
    }
    Up2 -> when (position) {
        GestureAnimationPosition.Left -> -90f
        GestureAnimationPosition.Right -> 90f
        GestureAnimationPosition.Bottom -> -90f
    }
    Down2 -> when (position) {
        GestureAnimationPosition.Left -> 90f
        GestureAnimationPosition.Right -> -90f
        GestureAnimationPosition.Bottom -> 90f
    }
}
