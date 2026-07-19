package com.slideindex.app.overlay

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatBallDragSessionTest {
  private val screenHeight = 2400f
  private val screenWidth = 1080f
  private val ballSizePx = 144f
  private val density = 3f
  private val marginPx = 24

  @Test
  fun pointer_mode_keeps_pick_above_ball_at_mid_screen() {
    val session = FloatBallDragSession()
    val settings = AppSettings(floatBallPickBottomTransitionFraction = 0.05f)
    val centerY = screenHeight * 0.55f

    session.armAtTouch(
      settings = settings,
      screenX = 500f,
      screenY = centerY,
      ballCenterX = 500f,
      ballCenterY = centerY,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = FloatBallSide.RIGHT,
    )
    session.onFingerMove(0f, 40f)

    val pick = session.computePick(
      settings = settings,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    )
    assertTrue(pick.y < centerY)
  }

  @Test
  fun right_line_drag_pick_stays_ball_relative_before_pointer_mode() {
    val session = FloatBallDragSession()
    val settings = AppSettings()
    val centerY = screenHeight * 0.55f
    val fingerX = screenWidth - 36f
    val ballCenterX = screenWidth - ballSizePx / 2f

    session.armAtTouch(
      settings = settings,
      screenX = fingerX,
      screenY = centerY,
      ballCenterX = ballCenterX,
      ballCenterY = centerY,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = FloatBallSide.RIGHT,
      anchorPickAtFinger = false,
    )

    val pick = session.computePick(
      settings = settings,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    )
    assertTrue(pick.x > fingerX)
  }

  @Test
  fun left_line_drag_pick_follows_finger_before_pointer_mode() {
    val session = FloatBallDragSession()
    val settings = AppSettings()
    val centerY = screenHeight * 0.55f
    val fingerX = 36f

    session.armAtTouch(
      settings = settings,
      screenX = fingerX,
      screenY = centerY,
      ballCenterX = 80f,
      ballCenterY = centerY,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = FloatBallSide.LEFT,
      anchorPickAtFinger = true,
    )

    val pick = session.computePick(
      settings = settings,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    )
    assertEquals(fingerX, pick.x, 0.5f)
  }
}
