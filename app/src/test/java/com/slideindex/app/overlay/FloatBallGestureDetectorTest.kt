package com.slideindex.app.overlay

import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FreeWindowMode
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
@Config(manifest = Config.NONE, sdk = [34])
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
            detector.predictSwipeGesture(dx = 0f, dy = -320f),
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
                settings = AppSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
                density = 3f,
                onPickStart = { _, _, _, _ -> },
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
    fun `micro rebound before release still fires swipe gesture`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector { type, _, _ -> fired = type }
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val moveDown = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        val moveRebound = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 100f, 335f, 0)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 100f, 335f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(moveDown)
        detector.onTouchEvent(moveRebound)
        detector.onTouchEvent(up)
        assertEquals(FloatBallGestureType.SWIPE_DOWN_SHORT, fired)
        down.recycle()
        moveDown.recycle()
        moveRebound.recycle()
        up.recycle()
    }

    @Test
    fun `single tap does not start pick drag`() {
        var pickStarted = false
        var pickEnded = false
        var pickCancelled = false
        val detector = newDetector(
            onGesture = { _, _, _ -> },
            onPickStart = { _, _, _, _ -> pickStarted = true },
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
            onPickStart = { _, _, _, _ -> pickStarted = true },
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
            onPickStart = { _, _, _, _ -> pickStarted = true },
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

    @Test
    fun `pause lock finishes pick before gesture window`() {
        var pickEnded = false
        var pickCancelled = false
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            onGesture = { type, _, _ -> fired = type },
            onPickEnd = { pickEnded = true },
            onPickCancel = { pickCancelled = true },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        val up = MotionEvent.obtain(0, 200, MotionEvent.ACTION_UP, 100f, 350f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(move)
        detector.lockPickFromPause()
        detector.onTouchEvent(up)
        assertTrue(pickEnded)
        assertFalse(pickCancelled)
        assertNull(fired)
        down.recycle()
        move.recycle()
        up.recycle()
    }

    @Test
    fun `unlock pause before gesture window restores swipe on release`() {
        var pickEnded = false
        var pickCancelled = false
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            onGesture = { type, _, _ -> fired = type },
            onPickEnd = { pickEnded = true },
            onPickCancel = { pickCancelled = true },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        val up = MotionEvent.obtain(0, 200, MotionEvent.ACTION_UP, 100f, 350f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(move)
        detector.lockPickFromPause()
        detector.unlockPickFromPause()
        detector.onTouchEvent(up)
        assertFalse(pickEnded)
        assertTrue(pickCancelled)
        assertEquals(FloatBallGestureType.SWIPE_DOWN_SHORT, fired)
        down.recycle()
        move.recycle()
        up.recycle()
    }

    @Test
    fun `pick preview callbacks fire before drag starts and cancel on tap`() {
        var previewStarted = false
        var previewCancelled = false
        var previewProgress = -1f
        var pickStarted = false
        val detector = newDetector(
            onPickStart = { _, _, _, _ -> pickStarted = true },
            onPickPreviewStart = { _, _ -> previewStarted = true },
            onPickPreviewProgress = { progress -> previewProgress = progress },
            onPickPreviewCancel = { previewCancelled = true },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 210f, 0)
        val up = MotionEvent.obtain(0, 100, MotionEvent.ACTION_UP, 100f, 210f, 0)
        detector.onTouchEvent(down)
        assertTrue(previewStarted)
        assertFalse(pickStarted)
        detector.onTouchEvent(move)
        assertTrue(previewProgress in 0f..1f)
        detector.onTouchEvent(up)
        assertTrue(previewCancelled)
        assertFalse(pickStarted)
        down.recycle()
        move.recycle()
        up.recycle()
    }

    @Test
    fun `slop phase move fires preview move before pick starts`() {
        var previewMoveCount = 0
        var pickStarted = false
        val detector = newDetector(
            onPickStart = { _, _, _, _ -> pickStarted = true },
            onPickPreviewMove = { _, _, _, _ -> previewMoveCount++ },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 205f, 0)
        val up = MotionEvent.obtain(0, 100, MotionEvent.ACTION_UP, 100f, 205f, 0)
        detector.onTouchEvent(down)
        assertEquals(1, previewMoveCount)
        detector.onTouchEvent(move)
        assertEquals(2, previewMoveCount)
        assertFalse(pickStarted)
        detector.onTouchEvent(up)
        assertFalse(pickStarted)
        down.recycle()
        move.recycle()
        up.recycle()
    }

    @Test
    fun `lag jump starts pick without double calling drag on transition frame`() {
        var startFingerX = -1f
        var startFingerY = -1f
        var dragFingerX = -1f
        var dragFingerY = -1f
        var dragCallCount = 0

        val detector = FloatBallGestureDetector()
        detector.bind(
            settings = AppSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
            density = 3f,
            onPickStart = { _, _, fingerX, fingerY ->
                startFingerX = fingerX
                startFingerY = fingerY
            },
            onPickDrag = { fingerX, fingerY ->
                dragFingerX = fingerX
                dragFingerY = fingerY
                dragCallCount++
            },
            onPickEnd = {},
            onPickCancel = {},
            onGesture = { _, _, _ -> },
        )

        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1050f, 500f, 0)
        // Lag jump: first move is already 200px away
        val moveJump = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 850f, 500f, 0)
        val moveNext = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 800f, 500f, 0)

        detector.onTouchEvent(down)
        detector.onTouchEvent(moveJump)

        // onPickStart receives 850f, onPickDrag NOT called on same frame
        assertEquals(850f, startFingerX, 0.001f)
        assertEquals(500f, startFingerY, 0.001f)
        assertEquals(0, dragCallCount)

        // on next move, onPickDrag receives 800f
        detector.onTouchEvent(moveNext)
        assertEquals(800f, dragFingerX, 0.001f)
        assertEquals(500f, dragFingerY, 0.001f)
        assertEquals(1, dragCallCount)

        down.recycle()
        moveJump.recycle()
        moveNext.recycle()
    }

    private fun newDetector(
        onPickStart: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> },
        onPickEnd: () -> Unit = {},
        onPickCancel: () -> Unit = {},
        onPickPreviewStart: (Float, Float) -> Unit = { _, _ -> },
        onPickPreviewProgress: (Float) -> Unit = {},
        onPickPreviewMove: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> },
        onPickPreviewCancel: () -> Unit = {},
        onGesture: (FloatBallGestureType, Float, Float) -> Unit = { _, _, _ -> },
    ): FloatBallGestureDetector {
        val detector = FloatBallGestureDetector()
        detector.bind(
            settings = AppSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
            density = 3f,
            onPickStart = onPickStart,
            onPickDrag = { _, _ -> },
            onPickEnd = onPickEnd,
            onPickCancel = onPickCancel,
            onGesture = onGesture,
            onPickPreviewStart = onPickPreviewStart,
            onPickPreviewProgress = onPickPreviewProgress,
            onPickPreviewMove = onPickPreviewMove,
            onPickPreviewCancel = onPickPreviewCancel,
        )
        return detector
    }
}
