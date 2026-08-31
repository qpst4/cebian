package com.slideindex.app.overlay

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSettings
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.FreeWindowSettings
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatBallEdgePickReplayTest {
    private val screenWidth = 1080f
    private val screenHeight = 2400f
    private val density = 3f
    private val marginPx = 24

    private fun testSettings(): AppSettings = AppSettings(
        freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
        floatBall = FloatBallSettings(
            floatBallPointerSpeedFraction = 0.52f,
            floatBallPointerSlopDp = 8f,
        ),
    )

    @Test
    fun replay_from_right_edge_to_left_screen_moves_pick_into_left_half() {
        val session = FloatBallDragSession()
        val settings = testSettings()
        val ballSizePx = settings.floatBallSizeDp.coerceIn(36f, 72f) * density
        val gestureStartRawY = screenHeight * 0.5f
        val triggerRawX = screenWidth * 0.08f
        val triggerRawY = screenHeight * 0.45f

        FloatBallEdgePickReplay.replayToTrigger(
            session = session,
            settings = settings,
            edgeSide = FloatBallSide.RIGHT,
            gestureStartRawY = gestureStartRawY,
            triggerRawX = triggerRawX,
            triggerRawY = triggerRawY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
            marginPx = marginPx,
        )

        val pick = session.computePick(
            settings = settings,
            ballSizePx = ballSizePx,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
            marginPx = marginPx,
        )
        assertTrue(
            "pick should reach left half after replay, was ${pick.x}",
            pick.x < screenWidth / 2f,
        )
        assertTrue(session.pointerModeActive)
    }
}
