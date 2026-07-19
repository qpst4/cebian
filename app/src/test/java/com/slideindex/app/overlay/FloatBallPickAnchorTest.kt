package com.slideindex.app.overlay

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatBallPickAnchorTest {
  private val screenHeight = 2400f
  private val screenWidth = 1080f
  private val ballSizePx = 144f
  private val density = 3f

  @Test
  fun pick_is_above_ball_when_far_from_bottom() {
    val pick = FloatBallPickAnchor.pickPointForBallCenter(
      settings = AppSettings(),
      ballCenterX = 1000f,
      ballCenterY = 600f,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = FloatBallSide.RIGHT,
    )
    assertTrue(pick.y < 600f)
  }

  @Test
  fun left_docked_pick_is_left_of_ball_center() {
    val centerX = 80f
    val pick = FloatBallPickAnchor.pickPointForBallCenter(
      settings = AppSettings(),
      ballCenterX = centerX,
      ballCenterY = 600f,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = FloatBallSide.LEFT,
    )
    assertTrue(pick.x < centerX)
  }

  @Test
  fun right_docked_pick_is_right_of_ball_center() {
    val centerX = 1000f
    val pick = FloatBallPickAnchor.pickPointForBallCenter(
      settings = AppSettings(),
      ballCenterX = centerX,
      ballCenterY = 600f,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = FloatBallSide.RIGHT,
    )
    assertTrue(pick.x > centerX)
  }

  @Test
  fun finger_pick_on_left_edge_can_reach_screen_edge() {
    val pick = FloatBallPickAnchor.pickPointForFinger(
      fingerX = 12f,
      ballCenterY = 600f,
      ballSizePx = ballSizePx,
      settings = AppSettings(),
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = FloatBallSide.LEFT,
    )
    assertEquals(12f, pick.x, 0.5f)
  }

  @Test
  fun horizontal_offset_mirrors_by_dock_side() {
    val gapPx = 48f * density
    val rightOffset = FloatBallPickAnchor.horizontalOffsetPx(gapPx, FloatBallSide.RIGHT)
    val leftOffset = FloatBallPickAnchor.horizontalOffsetPx(gapPx, FloatBallSide.LEFT)
    assertTrue(rightOffset > 0f)
    assertTrue(leftOffset < 0f)
    assertEquals(-rightOffset, leftOffset, 0.001f)
  }
}
