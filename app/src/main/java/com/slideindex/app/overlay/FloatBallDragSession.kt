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
  private var pickDockSide: FloatBallSide = FloatBallSide.RIGHT
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
    pickDockSide = FloatBallSide.RIGHT
    pointerModeActive = false
  }

  fun armAtTouch(
    settings: AppSettings,
    touchDownX: Float,
    touchDownY: Float,
    fingerX: Float,
    fingerY: Float,
    ballCenterX: Float,
    ballCenterY: Float,
    ballSizePx: Float,
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    pickDockSide: FloatBallSide,
  ) {
    this.pickDockSide = pickDockSide
    dragFingerX = fingerX
    dragFingerY = fingerY
    dragFingerAnchorX = fingerX
    dragFingerAnchorY = fingerY
    dragJoystickOffsetX = ballCenterX - touchDownX
    dragJoystickOffsetY = ballCenterY - touchDownY
    pointerModeActive = false
    establishPointerTravel(settings, screenWidth, screenHeight)

    val edgeDockSide = FloatBallPickAnchor.dockSideForBallCenter(
      ballCenterX = ballCenterX,
      screenWidth = screenWidth,
      fallbackDockSide = pickDockSide,
    )
    val pick = edgeAnchoredPick(
      settings = settings,
      ballCenterY = ballCenterY,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = edgeDockSide,
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
    val edgeDockSide = FloatBallPickAnchor.dockSideForBallCenter(
      ballCenterX = center.x,
      screenWidth = screenWidth,
      fallbackDockSide = pickDockSide,
    )
    val edgePick = edgeAnchoredPick(
      settings = settings,
      ballCenterY = center.y,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = edgeDockSide,
    )

    if (!pointerModeActive) {
      // 手势层已过 slop 才进入取词拖；此处不再重复 slop，手指一动即进入 pointer 模式。
      if (fingerTravelPx() <= 0f) {
        return edgePick
      }
      dragPointerAnchorX = edgePick.x
      dragPointerAnchorY = edgePick.y
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
    return freePick
  }

  fun refreshPointerTravel(settings: AppSettings, screenWidth: Float, screenHeight: Float) {
    if (pointerTravelWidth <= 0f && pointerTravelHeight <= 0f) return
    establishPointerTravel(settings, screenWidth, screenHeight)
  }

  private fun edgeAnchoredPick(
    settings: AppSettings,
    ballCenterY: Float,
    ballSizePx: Float,
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    dockSide: FloatBallSide,
  ): Offset = FloatBallPickAnchor.pickPointAtEdge(
    settings = settings,
    ballCenterY = ballCenterY,
    ballSizePx = ballSizePx,
    screenWidth = screenWidth,
    screenHeight = screenHeight,
    density = density,
    dockSide = dockSide,
  )

  private fun establishPointerTravel(settings: AppSettings, screenWidth: Float, screenHeight: Float) {
    val horizontalSpeed = settings.floatBallPointerSpeedFraction.coerceIn(
      FloatingPointerBounds.SENSITIVITY_MIN,
      FloatingPointerBounds.SENSITIVITY_MAX,
    )
    val verticalSpeed = settings.floatBallPointerSpeedVerticalFraction.coerceIn(
      FloatingPointerBounds.SENSITIVITY_MIN,
      FloatingPointerBounds.SENSITIVITY_MAX,
    )
    val (travelWidth, _) = FloatingPointerBounds.effectivePointerTravelForSpeed(
      speedFraction = horizontalSpeed,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
    )
    val (_, travelHeight) = FloatingPointerBounds.effectivePointerTravelForSpeed(
      speedFraction = verticalSpeed,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
    )
    pointerTravelWidth = travelWidth
    pointerTravelHeight = travelHeight
  }
}
