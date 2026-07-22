package com.slideindex.app.overlay

import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

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
    fun `locked side axis ignores later vertical displacement for hint`() {
        val detector = newDetector()
        val axis = FloatBallGestureDetector.LockedSwipeAxis.SIDE
        val (projDx, projDy) = detector.projectedDisplacement(
            totalDx = 120f,
            totalDy = 200f,
            lockedAxis = axis,
        )
        assertEquals(
            FloatBallGestureType.SWIPE_SIDE_SHORT,
            detector.predictSwipeGesture(projDx, projDy),
        )
        assertEquals(
            FloatBallGestureType.SWIPE_DOWN_SHORT,
            detector.predictSwipeGesture(dx = 120f, dy = 200f),
        )
    }

    @Test
    fun `resolveSwipeAxis locks first qualifying horizontal swipe`() {
        val detector = newDetector()
        assertEquals(
            FloatBallGestureDetector.LockedSwipeAxis.SIDE,
            detector.resolveSwipeAxis(dx = 120f, dy = 30f),
        )
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

    @Test
    fun `single tap fires after double tap detection window`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector { type, _, _ -> fired = type }
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val up = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, 100f, 200f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(up)
        assertNull(fired)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertEquals(FloatBallGestureType.SINGLE_TAP, fired)
        down.recycle()
        up.recycle()
    }

    @Test
    fun `drag away and back to origin does not fire single tap`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector { type, _, _ -> fired = type }
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val moveAway = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 300f, 0)
        val moveBack = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 100f, 200f, 0)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 100f, 200f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(moveAway)
        detector.onTouchEvent(moveBack)
        detector.onTouchEvent(up)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertNull(fired)
        down.recycle()
        moveAway.recycle()
        moveBack.recycle()
        up.recycle()
    }

    @Test
    fun `double tap cancels pending single tap`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector { type, _, _ -> fired = type }
        val firstDown = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val firstUp = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, 100f, 200f, 0)
        val secondDown = MotionEvent.obtain(0, 100, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val secondUp = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 100f, 200f, 0)
        detector.onTouchEvent(firstDown)
        detector.onTouchEvent(firstUp)
        detector.onTouchEvent(secondDown)
        detector.onTouchEvent(secondUp)
        assertEquals(FloatBallGestureType.DOUBLE_TAP, fired)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertEquals(FloatBallGestureType.DOUBLE_TAP, fired)
        firstDown.recycle()
        firstUp.recycle()
        secondDown.recycle()
        secondUp.recycle()
    }

    @Test
    fun `swipe down then reverse before release does not fire swipe gesture`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector { type, _, _ -> fired = type }
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val moveDown = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        val moveBack = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 100f, 250f, 0)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 100f, 250f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(moveDown)
        detector.onTouchEvent(moveBack)
        detector.onTouchEvent(up)
        assertNull(fired)
        down.recycle()
        moveDown.recycle()
        moveBack.recycle()
        up.recycle()
    }

    @Test
    fun `swipe down reverse then swipe down again fires swipe gesture`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector { type, _, _ -> fired = type }
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val moveDown = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        val moveBack = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 100f, 250f, 0)
        val moveDownAgain = MotionEvent.obtain(0, 150, MotionEvent.ACTION_MOVE, 100f, 360f, 0)
        val up = MotionEvent.obtain(0, 200, MotionEvent.ACTION_UP, 100f, 360f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(moveDown)
        detector.onTouchEvent(moveBack)
        detector.onTouchEvent(moveDownAgain)
        detector.onTouchEvent(up)
        assertEquals(FloatBallGestureType.SWIPE_DOWN_SHORT, fired)
        down.recycle()
        moveDown.recycle()
        moveBack.recycle()
        moveDownAgain.recycle()
        up.recycle()
    }

    @Test
    fun `reverse along locked axis disarms gesture hint`() {
        val hints = mutableListOf<FloatBallGestureType?>()
        val detector = newDetector(
            onGesture = { _, _, _ -> },
        ).also {
            it.bind(
                settings = AppSettings(),
                density = 3f,
                onPickStart = { _, _ -> },
                onPickDrag = { _, _ -> },
                onPickEnd = {},
                onPickCancel = {},
                onGesture = { _, _, _ -> },
                onGestureHint = { hint -> hints.add(hint) },
            )
        }
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val moveDown = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        val moveBack = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 100f, 250f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(moveDown)
        detector.onTouchEvent(moveBack)
        assertEquals(FloatBallGestureType.SWIPE_DOWN_SHORT, hints.last { it != null })
        assertNull(hints.last())
        down.recycle()
        moveDown.recycle()
        moveBack.recycle()
    }

    @Test
    fun `updateGestureArmState disarms on reverse and re-arms on forward swipe`() {
        val detector = newDetector()
        val axis = FloatBallGestureDetector.LockedSwipeAxis.DOWN
        detector.updateGestureArmState(
            totalDx = 0f,
            totalDy = 150f,
            incrementalDx = 0f,
            incrementalDy = 150f,
            lockedAxis = axis,
            forwardSign = 1f,
        )
        assertEquals(true, detector.isGestureArmedForTest())
        detector.updateGestureArmState(
            totalDx = 0f,
            totalDy = 80f,
            incrementalDx = 0f,
            incrementalDy = -70f,
            lockedAxis = axis,
            forwardSign = 1f,
        )
        assertEquals(false, detector.isGestureArmedForTest())
        detector.updateGestureArmState(
            totalDx = 0f,
            totalDy = 150f,
            incrementalDx = 0f,
            incrementalDy = 70f,
            lockedAxis = axis,
            forwardSign = 1f,
        )
        assertEquals(true, detector.isGestureArmedForTest())
    }

    @Test
    fun `single tap does not start pick drag`() {
        var pickStarted = false
        var pickEnded = false
        var pickCancelled = false
        val detector = newDetector(
            onGesture = { _, _, _ -> },
            onPickStart = { _, _ -> pickStarted = true },
            onPickEnd = { pickEnded = true },
            onPickCancel = { pickCancelled = true },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val up = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, 100f, 200f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(up)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertFalse(pickStarted)
        assertFalse(pickEnded)
        assertFalse(pickCancelled)
        down.recycle()
        up.recycle()
    }

    @Test
    fun `long press does not start or finish pick`() {
        var pickStarted = false
        var pickEnded = false
        var pickCancelled = false
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            onGesture = { type, _, _ -> fired = type },
            onPickStart = { _, _ -> pickStarted = true },
            onPickEnd = { pickEnded = true },
            onPickCancel = { pickCancelled = true },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val up = MotionEvent.obtain(0, 900, MotionEvent.ACTION_UP, 100f, 200f, 0)
        detector.onTouchEvent(down)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertEquals(FloatBallGestureType.LONG_PRESS, fired)
        detector.onTouchEvent(up)
        assertFalse(pickStarted)
        assertFalse(pickEnded)
        assertFalse(pickCancelled)
        down.recycle()
        up.recycle()
    }

    @Test
    fun `drag beyond slop starts pick and drag hold finishes pick`() {
        var pickStarted = false
        var pickEnded = false
        var pickCancelled = false
        val detector = newDetector(
            onPickStart = { _, _ -> pickStarted = true },
            onPickEnd = { pickEnded = true },
            onPickCancel = { pickCancelled = true },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 300f, 0)
        val up = MotionEvent.obtain(0, 900, MotionEvent.ACTION_UP, 100f, 300f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(move)
        assertTrue(pickStarted)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        detector.onTouchEvent(up)
        assertTrue(pickEnded)
        assertFalse(pickCancelled)
        down.recycle()
        move.recycle()
        up.recycle()
    }

    private fun newDetector(
        onPickStart: (Float, Float) -> Unit = { _, _ -> },
        onPickEnd: () -> Unit = {},
        onPickCancel: () -> Unit = {},
        onGesture: (FloatBallGestureType, Float, Float) -> Unit = { _, _, _ -> },
    ): FloatBallGestureDetector {
        val detector = FloatBallGestureDetector()
        detector.bind(
            settings = AppSettings(),
            density = 3f,
            onPickStart = onPickStart,
            onPickDrag = { _, _ -> },
            onPickEnd = onPickEnd,
            onPickCancel = onPickCancel,
            onGesture = onGesture,
        )
        return detector
    }
}
