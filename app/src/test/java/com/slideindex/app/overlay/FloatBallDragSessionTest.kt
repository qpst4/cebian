package com.slideindex.app.overlay

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSettings
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FreeWindowMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatBallDragSessionTest {
  private val screenHeight = 2400f
  private val screenWidth = 1080f
  private val ballSizePx = 144f
  private val density = 3f
  private val marginPx = 24

  private fun testSettings(
    floatBallPickBottomTransitionFraction: Float = 0.22f,
    floatBallPointerSpeedVerticalFraction: Float = 0.35f,
    floatBallPointerSlopDp: Float = 8f,
  ): AppSettings = AppSettings(
    freeWindowModeId = FreeWindowMode.STANDARD.id,
    floatBall = FloatBallSettings(
      floatBallPickBottomTransitionFraction = floatBallPickBottomTransitionFraction,
      floatBallPointerSpeedVerticalFraction = floatBallPointerSpeedVerticalFraction,
      floatBallPointerSlopDp = floatBallPointerSlopDp,
    ),
  )

  @Test
  fun pointer_mode_keeps_pick_above_ball_at_mid_screen() {
    val session = FloatBallDragSession()
    val settings = testSettings(floatBallPickBottomTransitionFraction = 0.05f)
    val centerY = screenHeight * 0.55f

    session.armAtTouch(
      settings = settings,
      touchDownX = 500f,
      touchDownY = centerY,
      fingerX = 500f,
      fingerY = centerY,
      ballCenterX = 500f,
      ballCenterY = centerY,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.RIGHT,
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
  fun pointer_mode_vertical_speed_scales_y_movement() {
    val centerY = screenHeight * 0.55f
    val fingerMoveY = 120f

    fun pickYForVerticalSpeed(verticalSpeed: Float): Float {
      val session = FloatBallDragSession()
      val settings = testSettings(
        floatBallPointerSpeedVerticalFraction = verticalSpeed,
      )
      session.armAtTouch(
        settings = settings,
        touchDownX = 500f,
        touchDownY = centerY,
        fingerX = 500f,
        fingerY = centerY,
        ballCenterX = 500f,
        ballCenterY = centerY,
        ballSizePx = ballSizePx,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        density = density,
        pickDockSide = FloatBallSide.RIGHT,
      )
      session.onFingerMove(0f, fingerMoveY)
      session.computePick(
        settings = settings,
        ballSizePx = ballSizePx,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        density = density,
        marginPx = marginPx,
      )
      session.onFingerMove(0f, 80f)
      return session.computePick(
        settings = settings,
        ballSizePx = ballSizePx,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        density = density,
        marginPx = marginPx,
      ).y
    }

    val slowY = pickYForVerticalSpeed(FloatingPointerBounds.SENSITIVITY_MIN)
    val fastY = pickYForVerticalSpeed(FloatingPointerBounds.SENSITIVITY_MAX)
    assertTrue(fastY > slowY)
  }

  @Test
  fun right_docked_pick_snaps_to_right_edge_before_pointer_mode() {
    val session = FloatBallDragSession()
    val settings = testSettings()
    val centerY = screenHeight * 0.55f
    val fingerX = screenWidth - 36f
    val ballCenterX = screenWidth - ballSizePx / 2f

    session.armAtTouch(
      settings = settings,
      touchDownX = fingerX,
      touchDownY = centerY,
      fingerX = fingerX,
      fingerY = centerY,
      ballCenterX = ballCenterX,
      ballCenterY = centerY,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.RIGHT,
    )

    val pick = session.computePick(
      settings = settings,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    )
    assertEquals(screenWidth, pick.x, 0.5f)
  }

  @Test
  fun line_drag_pick_snaps_to_ball_side_not_opposite_edge() {
    val session = FloatBallDragSession()
    val settings = testSettings()
    val centerY = screenHeight * 0.55f

    session.armAtTouch(
      settings = settings,
      touchDownX = 36f,
      touchDownY = centerY,
      fingerX = 36f,
      fingerY = centerY,
      ballCenterX = 80f,
      ballCenterY = centerY,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.LEFT,
    )

    val pick = session.computePick(
      settings = settings,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    )
    assertEquals(0f, pick.x, 0.5f)
  }

  @Test
  fun drag_maintains_dock_side_anchor_without_jumping_to_opposite_edge() {
    val session = FloatBallDragSession()
    val settings = testSettings(floatBallPointerSlopDp = 32f)
    val centerY = screenHeight * 0.55f

    session.armAtTouch(
      settings = settings,
      touchDownX = screenWidth - 36f,
      touchDownY = centerY,
      fingerX = screenWidth - 36f,
      fingerY = centerY,
      ballCenterX = screenWidth - ballSizePx / 2f,
      ballCenterY = centerY,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.RIGHT,
    )
    session.updateFingerPosition(screenWidth - 36f - 50f, centerY)

    val pick = session.computePick(
      settings = settings,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    )
    // Pointer smoothly moves left from right edge (1080) towards left, without flipping dock anchor to 0
    assertTrue(pick.x < screenWidth)
    assertTrue(pick.x > 800f)
  }

  @Test
  fun lag_jump_absolute_finger_position_aligns_ball_with_finger() {
    val session = FloatBallDragSession()
    val settings = testSettings()
    val centerY = screenHeight * 0.55f
    val touchDownX = screenWidth - 36f
    val ballCenterX = screenWidth - ballSizePx / 2f

    // 1. Arm at initial touch down
    session.armAtTouch(
      settings = settings,
      touchDownX = touchDownX,
      touchDownY = centerY,
      fingerX = touchDownX,
      fingerY = centerY,
      ballCenterX = ballCenterX,
      ballCenterY = centerY,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.RIGHT,
    )

    // 2. Under heavy load, first move jumps 200px to the left in one single event
    val jumpedFingerX = touchDownX - 200f
    session.updateFingerPosition(jumpedFingerX, centerY)

    val center = session.ballCenter()
    val expectedCenterX = jumpedFingerX + (ballCenterX - touchDownX)
    assertEquals(expectedCenterX, center.x, 0.001f)
    assertEquals(centerY, center.y, 0.001f)
  }

  @Test
  fun joystick_offset_uses_touch_down_not_slop_finger_position() {
    val session = FloatBallDragSession()
    val settings = testSettings()
    val ballCenterX = 500f
    val ballCenterY = screenHeight * 0.55f
    val touchDownX = ballCenterX
    val touchDownY = ballCenterY
    val slopFingerX = touchDownX + 24f
    val slopFingerY = touchDownY + 24f

    session.armAtTouch(
      settings = settings,
      touchDownX = touchDownX,
      touchDownY = touchDownY,
      fingerX = slopFingerX,
      fingerY = slopFingerY,
      ballCenterX = ballCenterX,
      ballCenterY = ballCenterY,
      ballSizePx = ballSizePx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.RIGHT,
    )

    val center = session.ballCenter()
    assertEquals(slopFingerX, center.x, 0.5f)
    assertEquals(slopFingerY, center.y, 0.5f)
  }
}
