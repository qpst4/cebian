package com.slideindex.app.overlay

import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class FloatBallGestureDetectorTest {
    @Test
    fun `predictSwipeGesture returns up short before threshold`() {
        val detector = newDetector()
        assertEquals(
            FloatBallGestureType.SWIPE_UP_SHORT,
            detector.predictSwipeGesture(dx = 0f, dy = -80f),
        )
    }

    @Test
    fun `predictSwipeGesture returns up long past threshold`() {
        val detector = newDetector()
        assertEquals(
            FloatBallGestureType.SWIPE_UP_LONG,
            detector.predictSwipeGesture(dx = 0f, dy = -260f),
        )
    }

    @Test
    fun `predictSwipeGesture returns null for ambiguous diagonal`() {
        val detector = newDetector()
        assertNull(detector.predictSwipeGesture(dx = 80f, dy = -80f))
    }

    @Test
    fun `hint layout is top-left of finger when dragging from right edge`() {
        val (x, y) = FloatBallGestureHintWindow.hintTopLeftForFingerPx(
            fingerX = 300f,
            fingerY = 500f,
            dockSide = FloatBallSide.RIGHT,
            hintSizePx = 48,
            gapPx = 48,
        )
        assertEquals(204, x)
        assertEquals(404, y)
    }

    @Test
    fun `hint layout is top-right of finger when dragging from left edge`() {
        val (x, y) = FloatBallGestureHintWindow.hintTopLeftForFingerPx(
            fingerX = 100f,
            fingerY = 500f,
            dockSide = FloatBallSide.LEFT,
            hintSizePx = 48,
            gapPx = 48,
        )
        assertEquals(148, x)
        assertEquals(404, y)
    }

    private fun newDetector(): FloatBallGestureDetector {
        val detector = FloatBallGestureDetector()
        detector.bind(
            settings = AppSettings(),
            density = 3f,
            onPickStart = { _, _ -> },
            onPickDrag = { _, _ -> },
            onPickEnd = {},
            onPickCancel = {},
            onGesture = { _, _, _ -> },
        )
        return detector
    }
}
