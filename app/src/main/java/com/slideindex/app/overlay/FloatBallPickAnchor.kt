package com.slideindex.app.overlay

import androidx.compose.ui.geometry.Offset
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide

/**
 * Continuous pick-point offset relative to the float-ball center (FV-style).
 * Default above the ball; smoothly transitions below only inside a band at the screen bottom edge.
 */
internal object FloatBallPickAnchor {
  private const val MIN_SCREEN_MARGIN_DP = 4f
  private const val BOTTOM_EDGE_INSET_DP = 8f
  private const val HORIZONTAL_OFFSET_FRACTION = 0.35f

  fun pickPointForBallCenter(
    settings: AppSettings,
    ballCenterX: Float,
    ballCenterY: Float,
    ballSizePx: Float,
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    dockSide: FloatBallSide,
  ): Offset {
    val gapPx = settings.floatBallPickOffsetDp.coerceIn(4f, 48f) * density
    val aboveOffsetY = -(ballSizePx / 2f + gapPx)
    val belowOffsetY = ballSizePx / 2f + gapPx

    val ballBottom = ballCenterY + ballSizePx / 2f
    val blend = bottomFlipBlend(
      ballBottomY = ballBottom,
      screenHeight = screenHeight,
      transitionBandPx = bottomTransitionBandPx(settings, screenHeight),
      edgeInsetPx = BOTTOM_EDGE_INSET_DP * density,
    )
    val offsetY = aboveOffsetY + (belowOffsetY - aboveOffsetY) * blend
    val offsetX = horizontalOffsetPx(gapPx, dockSide)

    return clampToScreen(
      x = ballCenterX + offsetX,
      y = ballCenterY + offsetY,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = dockSide,
    )
  }

  fun pickPointForFinger(
    fingerX: Float,
    ballCenterY: Float,
    ballSizePx: Float,
    settings: AppSettings,
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    dockSide: FloatBallSide,
  ): Offset {
    val ballPick = pickPointForBallCenter(
      settings = settings,
      ballCenterX = fingerX,
      ballCenterY = ballCenterY,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = dockSide,
    )
    return clampToScreen(
      x = fingerX,
      y = ballPick.y,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = dockSide,
    )
  }

  fun horizontalOffsetPx(gapPx: Float, dockSide: FloatBallSide): Float =
    when (dockSide) {
      FloatBallSide.LEFT -> -gapPx * HORIZONTAL_OFFSET_FRACTION
      FloatBallSide.RIGHT -> gapPx * HORIZONTAL_OFFSET_FRACTION
    }

  /**
   * 0 = pick above ball, 1 = pick below ball.
   * Transition only occurs while [ballBottomY] moves through the bottom edge band.
   */
  internal fun bottomFlipBlend(
    ballBottomY: Float,
    screenHeight: Float,
    transitionBandPx: Float,
    edgeInsetPx: Float,
  ): Float {
    val bandBottomY = screenHeight - edgeInsetPx
    val bandTopY = (bandBottomY - transitionBandPx).coerceAtLeast(0f)
    val linearT = when {
      ballBottomY <= bandTopY -> 0f
      ballBottomY >= bandBottomY -> 1f
      else -> (ballBottomY - bandTopY) / (bandBottomY - bandTopY)
    }
    return smoothstep(linearT)
  }

  internal fun bottomTransitionBandPx(settings: AppSettings, screenHeight: Float): Float =
    screenHeight * settings.floatBallPickBottomTransitionFraction.coerceIn(0.05f, 0.22f)

  fun clampToScreen(
    x: Float,
    y: Float,
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    dockSide: FloatBallSide? = null,
  ): Offset {
    val margin = MIN_SCREEN_MARGIN_DP * density
    val minX = if (dockSide == FloatBallSide.LEFT) 0f else margin
    val maxX = if (dockSide == FloatBallSide.RIGHT) screenWidth else screenWidth - margin
    return Offset(
      x = x.coerceIn(minX, maxX),
      y = y.coerceIn(margin, screenHeight - margin),
    )
  }

  private fun smoothstep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
  }
}
