package com.slideindex.app.overlay

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSettings
import com.slideindex.app.settings.FloatBallSide
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatBallLineDragSwapTest {
  @Test
  fun swap_when_ball_on_right_line_on_left() {
    val settings = AppSettings(
      floatBall = FloatBallSettings(
        floatBallPositionMode = FloatBallPositionMode.BOTH_EDGES,
        floatBallActiveSide = FloatBallSide.RIGHT,
      ),
    )
    assertEquals(
      FloatBallSide.LEFT,
      FloatBallLayout.activeSideAfterLineDragSwap(settings),
    )
  }

  @Test
  fun swap_when_ball_on_left_line_on_right() {
    val settings = AppSettings(
      floatBall = FloatBallSettings(
        floatBallPositionMode = FloatBallPositionMode.BOTH_EDGES,
        floatBallActiveSide = FloatBallSide.LEFT,
      ),
    )
    assertEquals(
      FloatBallSide.RIGHT,
      FloatBallLayout.activeSideAfterLineDragSwap(settings),
    )
  }
}
