package com.slideindex.app.overlay

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.FloatBallSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class TakeoverExtraRectsTest {
  private val density = 2f
  private val screenWidthPx = 1000
  private val screenHeightPx = 2000

  @Test
  fun floatBallDisabled_emitsNothing() {
    val rects = build(AppSettings(floatBall = FloatBallSettings(floatBallEnabled = false)))

    assertTrue(rects.isEmpty())
  }

  @Test
  fun dockedFloatBall_isTaggedToSides() {
    val rects = build(
      AppSettings(
        floatBall = FloatBallSettings(
          floatBallEnabled = true,
          floatBallPositionMode = FloatBallPositionMode.RIGHT,
          floatBallActiveSide = FloatBallSide.RIGHT,
        ),
      ),
    )

    val ball = rects.single { it.target == ModuleHookBridgeContract.TARGET_FLOAT_BALL }
    assertEquals(ModuleHookBridgeContract.GROUP_SIDES, ball.groups)
    // 贴右边缘：右边界就是屏幕右边界。
    assertEquals(1f, ball.rightFraction, 0.001f)
  }

  @Test
  fun customFloatBallInMiddle_isSkipped() {
    val rects = build(
      AppSettings(
        floatBall = FloatBallSettings(
          floatBallEnabled = true,
          floatBallPositionMode = FloatBallPositionMode.CUSTOM,
          floatBallCustomCenterXFraction = 0.5f,
          floatBallPositionYFraction = 0.5f,
        ),
      ),
    )

    // 方案 A：不贴任何系统手势带就不下发，避免在屏幕中部凭空吞流。
    assertTrue(rects.none { it.target == ModuleHookBridgeContract.TARGET_FLOAT_BALL })
  }

  @Test
  fun customFloatBallNearBottom_isTaggedToBottom() {
    val rects = build(
      AppSettings(
        floatBall = FloatBallSettings(
          floatBallEnabled = true,
          floatBallPositionMode = FloatBallPositionMode.CUSTOM,
          floatBallCustomCenterXFraction = 0.5f,
          floatBallPositionYFraction = 1f,
        ),
      ),
    )

    val ball = rects.single { it.target == ModuleHookBridgeContract.TARGET_FLOAT_BALL }
    assertEquals(ModuleHookBridgeContract.GROUP_BOTTOM, ball.groups)
  }

  @Test
  fun bothEdgesLine_isTaggedToSides() {
    val rects = build(
      AppSettings(
        floatBall = FloatBallSettings(
          floatBallEnabled = true,
          floatBallPositionMode = FloatBallPositionMode.BOTH_EDGES,
          floatBallActiveSide = FloatBallSide.RIGHT,
        ),
      ),
    )

    val line = rects.single { it.target == ModuleHookBridgeContract.TARGET_FLOAT_LINE }
    // 线条在悬浮球对侧（LEFT），贴左边缘。
    assertEquals(ModuleHookBridgeContract.GROUP_SIDES, line.groups)
    assertEquals(0f, line.leftFraction, 0.001f)
  }

  @Test
  fun singleSideMode_hasNoLine() {
    val rects = build(
      AppSettings(
        floatBall = FloatBallSettings(
          floatBallEnabled = true,
          floatBallPositionMode = FloatBallPositionMode.LEFT,
          floatBallActiveSide = FloatBallSide.LEFT,
        ),
      ),
    )

    assertTrue(rects.none { it.target == ModuleHookBridgeContract.TARGET_FLOAT_LINE })
  }

  @Test
  fun cornerWheel_emitsBothStripsForEnabledCornersOnly() {
    val rects = build(
      AppSettings(
        cornerGestureSettings = CornerGestureSettings(
          enabled = true,
          leftEnabled = true,
          rightEnabled = false,
        ),
      ),
    )

    assertEquals(2, rects.size)
    assertTrue(rects.all { it.target == ModuleHookBridgeContract.TARGET_CORNER_LEFT })
    assertTrue(rects.all { it.groups == ModuleHookBridgeContract.GROUP_BOTTOM })
    assertEquals(
      setOf(
        ModuleHookBridgeContract.CORNER_STRIP_VERTICAL,
        ModuleHookBridgeContract.CORNER_STRIP_HORIZONTAL,
      ),
      rects.map { it.strip }.toSet(),
    )
  }

  @Test
  fun cornerWheelDisabled_emitsNothing() {
    val rects = build(
      AppSettings(cornerGestureSettings = CornerGestureSettings(enabled = false, leftEnabled = true)),
    )

    assertTrue(rects.isEmpty())
  }

  private fun build(settings: AppSettings) = TakeoverExtraRects.build(
    settings = settings,
    screenWidthPx = screenWidthPx,
    screenHeightPx = screenHeightPx,
    density = density,
    isLandscape = false,
  )
}
