package com.slideindex.app.gesture

import com.slideindex.app.overlay.animation.GestureAnimationPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureAnimationProgressTest {

    @Test
    fun leftEdge_inwardSwipe_progressGrowsWithFingerDelta() {
        assertEquals(0f, GestureAnimationProgress.progress(
            position = GestureAnimationPosition.Left,
            originX = 12f,
            originY = 400f,
            fingerX = 0f,
            fingerY = 400f,
            swipeDirection = null,
        ), 0.01f)
        assertTrue(
            GestureAnimationProgress.progress(
                position = GestureAnimationPosition.Left,
                originX = 12f,
                originY = 400f,
                fingerX = 48f,
                fingerY = 420f,
                swipeDirection = SwipeDirection.IN,
            ) > 1f,
        )
    }

    @Test
    fun bottomEdge_inwardSwipe_usesOriginMinusFingerY() {
        val progress = GestureAnimationProgress.progress(
            position = GestureAnimationPosition.Bottom,
            originX = 540f,
            originY = 2200f,
            fingerX = 540f,
            fingerY = 0f,
            swipeDirection = null,
        )
        assertTrue(progress > 1f)
    }

    @Test
    fun topEdge_horizontalAlongEdge_usesAlongAxis() {
        val progress = GestureAnimationProgress.progress(
            position = GestureAnimationPosition.Top,
            originX = 300f,
            originY = 0f,
            fingerX = 360f,
            fingerY = 0f,
            swipeDirection = SwipeDirection.DOWN,
        )
        assertEquals(60f, progress, 0.01f)
        assertTrue(GestureAnimationProgress.isHorizontalAlongEdge(
            GestureAnimationPosition.Top,
            SwipeDirection.DOWN,
        ))
    }

    @Test
    fun topEdge_inwardSwipe_usesFingerMinusOriginY() {
        val progress = GestureAnimationProgress.progress(
            position = GestureAnimationPosition.Top,
            originX = 300f,
            originY = 12f,
            fingerX = 300f,
            fingerY = 80f,
            swipeDirection = SwipeDirection.IN,
        )
        assertEquals(68f, progress, 0.01f)
        assertFalse(GestureAnimationProgress.isHorizontalAlongEdge(
            GestureAnimationPosition.Top,
            SwipeDirection.IN,
        ))
    }
}
