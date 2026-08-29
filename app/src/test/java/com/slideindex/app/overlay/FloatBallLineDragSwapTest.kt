package com.slideindex.app.overlay

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSettings
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FreeWindowMode
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatBallLineDragSwapTest {
  private fun testSettings(activeSide: FloatBallSide) = AppSettings(
    freeWindowModeId = FreeWindowMode.STANDARD.id,
    floatBall = FloatBallSettings(
      floatBallPositionMode = FloatBallPositionMode.BOTH_EDGES,
      floatBallActiveSide = activeSide,
    ),
  )

  @Test
  fun swap_when_ball_on_right_line_on_left() {
    val settings = testSettings(FloatBallSide.RIGHT)
    assertEquals(
      FloatBallSide.LEFT,
      FloatBallLayout.activeSideAfterLineDragSwap(settings),
    )
  }

  @Test
  fun swap_when_ball_on_left_line_on_right() {
    val settings = testSettings(FloatBallSide.LEFT)
    assertEquals(
      FloatBallSide.RIGHT,
      FloatBallLayout.activeSideAfterLineDragSwap(settings),
    )
  }
}
