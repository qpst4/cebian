package com.slideindex.app.overlay

import androidx.compose.ui.geometry.Offset
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import kotlin.math.hypot
import kotlin.math.roundToInt

internal class FloatBallDragSession {
  var dragFingerX = 0f
  var dragFingerY = 0f
  private var dragFingerAnchorX = 0f
  private var dragFingerAnchorY = 0f
  private var dragPointerAnchorX = 0f
  private var dragPointerAnchorY = 0f
  var dragJoystickOffsetX = 0f
  var dragJoystickOffsetY = 0f
  private var pointerTravelWidth = 0f
  private var pointerTravelHeight = 0f
  private var dockSide: FloatBallSide = FloatBallSide.RIGHT
  private var lineDragPick = false
  var pointerModeActive = false
    private set

  fun reset() {
    dragFingerX = 0f
    dragFingerY = 0f
    dragFingerAnchorX = 0f
    dragFingerAnchorY = 0f
    dragPointerAnchorX = 0f
    dragPointerAnchorY = 0f
    dragJoystickOffsetX = 0f
    dragJoystickOffsetY = 0f
    pointerTravelWidth = 0f
    pointerTravelHeight = 0f
    dockSide = FloatBallSide.RIGHT
    lineDragPick = false
    pointerModeActive = false
  }

  fun armAtTouch(
    settings: AppSettings,
    screenX: Float,
    screenY: Float,
    ballCenterX: Float,
    ballCenterY: Float,
    ballSizePx: Float,
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    dockSide: FloatBallSide,
    anchorPickAtFinger: Boolean = false,
  ) {
    this.dockSide = dockSide
    lineDragPick = anchorPickAtFinger
    dragFingerX = screenX
    dragFingerY = screenY
    dragFingerAnchorX = screenX
    dragFingerAnchorY = screenY
    dragJoystickOffsetX = ballCenterX - screenX
    dragJoystickOffsetY = ballCenterY - screenY
    pointerModeActive = false
    establishPointerTravel(settings, screenWidth, screenHeight)

    val pick = initialPick(
      settings = settings,
      fingerX = screenX,
      ballCenterX = ballCenterX,
      ballCenterY = ballCenterY,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
    )
    dragPointerAnchorX = pick.x
    dragPointerAnchorY = pick.y
  }

  fun onFingerMove(dx: Float, dy: Float) {
    dragFingerX += dx
    dragFingerY += dy
  }

  fun fingerTravelPx(): Float =
    hypot(dragFingerX - dragFingerAnchorX, dragFingerY - dragFingerAnchorY)

  fun ballCenter(): Offset =
    Offset(dragFingerX + dragJoystickOffsetX, dragFingerY + dragJoystickOffsetY)

  fun clampedBallCenter(
    ballSizePx: Float,
    marginPx: Int,
    screenWidth: Int,
    screenHeight: Int,
  ): Offset {
    val center = ballCenter()
    val half = ballSizePx / 2f
    val minCenterX = -half
    val maxCenterX = screenWidth + half
    val minCenterY = marginPx + half
    val maxCenterY = screenHeight - marginPx - half
    return Offset(
      x = center.x.coerceIn(minCenterX, maxCenterX),
      y = center.y.coerceIn(minCenterY, maxCenterY),
    )
  }

  fun ballTopLeft(
    ballSizePx: Int,
    marginPx: Int,
    screenWidth: Int,
    screenHeight: Int,
  ): Pair<Int, Int> {
    val center = clampedBallCenter(ballSizePx.toFloat(), marginPx, screenWidth, screenHeight)
    val left = (center.x - ballSizePx / 2f).roundToInt()
    val top = (center.y - ballSizePx / 2f).roundToInt()
    return left to top
  }

  fun computePick(
    settings: AppSettings,
    ballSizePx: Float,
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    marginPx: Int,
  ): Offset {
    if (pointerTravelWidth <= 0f || pointerTravelHeight <= 0f) {
      establishPointerTravel(settings, screenWidth, screenHeight)
    }

    val center = clampedBallCenter(
      ballSizePx = ballSizePx,
      marginPx = marginPx,
      screenWidth = screenWidth.roundToInt(),
      screenHeight = screenHeight.roundToInt(),
    )
    val ballPick = FloatBallPickAnchor.pickPointForBallCenter(
      settings = settings,
      ballCenterX = center.x,
      ballCenterY = center.y,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = dockSide,
    )

    val slopPx = settings.floatBallPointerSlopDp.coerceIn(4f, 32f) * density
    if (!pointerModeActive && fingerTravelPx() < slopPx) {
      return if (lineDragPick) {
        fingerAnchoredPick(
          settings = settings,
          ballCenterY = center.y,
          ballSizePx = ballSizePx,
          screenWidth = screenWidth,
          screenHeight = screenHeight,
          density = density,
        )
      } else {
        ballPick
      }
    }

    if (!pointerModeActive) {
      val anchorPick = if (lineDragPick) {
        fingerAnchoredPick(
          settings = settings,
          ballCenterY = center.y,
          ballSizePx = ballSizePx,
          screenWidth = screenWidth,
          screenHeight = screenHeight,
          density = density,
        )
      } else {
        ballPick
      }
      dragPointerAnchorX = anchorPick.x
      dragPointerAnchorY = anchorPick.y
      dragFingerAnchorX = dragFingerX
      dragFingerAnchorY = dragFingerY
      pointerModeActive = true
    }

    val freePick = FloatingPointerBounds.pointerForFingerDeltaInArea(
      deltaX = dragFingerX - dragFingerAnchorX,
      deltaY = dragFingerY - dragFingerAnchorY,
      travelWidth = pointerTravelWidth,
      travelHeight = pointerTravelHeight,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      pointerAnchorX = dragPointerAnchorX,
      pointerAnchorY = dragPointerAnchorY,
    )
    // X moves across the full screen; Y stays ball-relative so the bottom transition band works.
    return Offset(x = freePick.x, y = ballPick.y)
  }

  fun refreshPointerTravel(settings: AppSettings, screenWidth: Float, screenHeight: Float) {
    if (pointerTravelWidth <= 0f && pointerTravelHeight <= 0f) return
    establishPointerTravel(settings, screenWidth, screenHeight)
  }

  private fun initialPick(
    settings: AppSettings,
    fingerX: Float,
    ballCenterX: Float,
    ballCenterY: Float,
    ballSizePx: Float,
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
  ): Offset {
    if (lineDragPick) {
      return fingerAnchoredPick(
        settings = settings,
        ballCenterY = ballCenterY,
        ballSizePx = ballSizePx,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        density = density,
      )
    }
    return FloatBallPickAnchor.pickPointForBallCenter(
      settings = settings,
      ballCenterX = ballCenterX,
      ballCenterY = ballCenterY,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = dockSide,
    )
  }

  private fun fingerAnchoredPick(
    settings: AppSettings,
    ballCenterY: Float,
    ballSizePx: Float,
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
  ): Offset = FloatBallPickAnchor.pickPointForFinger(
    fingerX = dragFingerX,
    ballCenterY = ballCenterY,
    ballSizePx = ballSizePx,
    settings = settings,
    screenWidth = screenWidth,
    screenHeight = screenHeight,
    density = density,
    dockSide = dockSide,
  )

  private fun establishPointerTravel(settings: AppSettings, screenWidth: Float, screenHeight: Float) {
    val speed = settings.floatBallPointerSpeedFraction.coerceIn(
      FloatingPointerBounds.SENSITIVITY_MIN,
      FloatingPointerBounds.SENSITIVITY_MAX,
    )
    val (travelWidth, travelHeight) = FloatingPointerBounds.effectivePointerTravelForSpeed(
      speedFraction = speed,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
    )
    pointerTravelWidth = travelWidth
    pointerTravelHeight = travelHeight
  }
}
