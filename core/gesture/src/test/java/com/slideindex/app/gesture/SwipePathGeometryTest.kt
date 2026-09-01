package com.slideindex.app.gesture

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SwipePathGeometryTest {

    @Test
    fun inwardDelta_leftPanel_positiveWhenMovingRight() {
        assertEquals(120f, SwipePathGeometry.inwardDelta(120f, PanelSide.LEFT), 0.01f)
    }

    @Test
    fun inwardDelta_rightPanel_positiveWhenMovingLeft() {
        assertEquals(120f, SwipePathGeometry.inwardDelta(-120f, PanelSide.RIGHT), 0.01f)
    }

    @Test
    fun classifySwipeTrigger_shortInwardSwipe_returnsShortSwipeIn() {
        val trigger = SwipePathGeometry.classifySwipeTrigger(
            inward = 100f,
            dy = 0f,
            distancePx = 80f,
            shortThresholdPx = 60f,
            longThresholdPx = 120f,
            angleConfig = GestureAngleConfig.DEFAULT,
        )

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, trigger)
    }

    @Test
    fun classifySwipeTrigger_longInwardSwipe_returnsLongSwipeIn() {
        val trigger = SwipePathGeometry.classifySwipeTrigger(
            inward = 150f,
            dy = 0f,
            distancePx = 150f,
            shortThresholdPx = 60f,
            longThresholdPx = 120f,
            angleConfig = GestureAngleConfig.DEFAULT,
        )

        assertEquals(GestureTriggerType.LONG_SWIPE_IN, trigger)
    }

    @Test
    fun classifySwipeTrigger_upwardSwipe_returnsShortSwipeUp() {
        val trigger = SwipePathGeometry.classifySwipeTrigger(
            inward = 40f,
            dy = -120f,
            distancePx = 130f,
            shortThresholdPx = 60f,
            longThresholdPx = 200f,
            angleConfig = GestureAngleConfig.DEFAULT,
        )

        assertEquals(GestureTriggerType.SHORT_SWIPE_UP, trigger)
    }

    @Test
    fun resolveSwipeDirection_leftEdgeHorizontal_returnsIn() {
        val strip = RectF(0f, 0f, 20f, 2000f)
        val direction = SwipePathGeometry.resolveSwipeDirection(
            side = PanelSide.LEFT,
            stripBounds = strip,
            startX = 5f,
            startY = 100f,
            fingerX = 90f,
            fingerY = 100f,
            angle = GestureAngle.DEFAULT_LEFT,
        )
        assertEquals(SwipeDirection.IN, direction)
    }

    @Test
    fun resolveSwipeDirection_rightEdgeHorizontal_returnsIn() {
        val strip = RectF(180f, 0f, 200f, 2000f)
        val direction = SwipePathGeometry.resolveSwipeDirection(
            side = PanelSide.RIGHT,
            stripBounds = strip,
            startX = 195f,
            startY = 100f,
            fingerX = 130f,
            fingerY = 100f,
            angle = GestureAngle.DEFAULT_LEFT,
        )
        assertEquals(SwipeDirection.IN, direction)
    }

    @Test
    fun classifySwipeTrigger_rightEdgeInward_returnsShortSwipeIn() {
        val strip = RectF(180f, 0f, 200f, 2000f)
        val trigger = SwipePathGeometry.classifySwipeTrigger(
            side = PanelSide.RIGHT,
            stripBounds = strip,
            startX = 195f,
            startY = 100f,
            fingerX = 130f,
            fingerY = 100f,
            shortThresholdPx = 60f,
            longThresholdPx = 120f,
            angle = GestureAngle.DEFAULT_LEFT,
        )
        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, trigger)
    }

    @Test
    fun classifySwipeTrigger_belowShortThreshold_returnsNull() {
        val trigger = SwipePathGeometry.classifySwipeTrigger(
            inward = 30f,
            dy = 0f,
            distancePx = 30f,
            shortThresholdPx = 60f,
            longThresholdPx = 120f,
            angleConfig = GestureAngleConfig.DEFAULT,
        )

        assertNull(trigger)
    }

    @Test
    fun resolveSwipeDirection_topEdgeHorizontalLeft_returnsUp() {
        val strip = RectF(400f, 0f, 600f, 66f)
        val direction = SwipePathGeometry.resolveSwipeDirection(
            side = PanelSide.TOP,
            stripBounds = strip,
            startX = 500f,
            startY = 10f,
            fingerX = 400f,
            fingerY = 10f,
            angle = GestureAngle.DEFAULT_TOP,
        )
        assertEquals(SwipeDirection.UP, direction)
    }

    @Test
    fun resolveSwipeDirection_topEdgeHorizontalRight_returnsDown() {
        val strip = RectF(400f, 0f, 600f, 66f)
        val direction = SwipePathGeometry.resolveSwipeDirection(
            side = PanelSide.TOP,
            stripBounds = strip,
            startX = 500f,
            startY = 10f,
            fingerX = 580f,
            fingerY = 10f,
            angle = GestureAngle.DEFAULT_TOP,
        )
        assertEquals(SwipeDirection.DOWN, direction)
    }

    @Test
    fun resolveSwipeDirection_topEdgeHorizontalLeft_notDiagonal() {
        val strip = RectF(400f, 0f, 600f, 66f)
        val direction = SwipePathGeometry.resolveSwipeDirection(
            side = PanelSide.TOP,
            stripBounds = strip,
            startX = 500f,
            startY = 10f,
            fingerX = 430f,
            fingerY = 10f,
            angle = GestureAngle.DEFAULT_TOP,
        )
        assertEquals(SwipeDirection.UP, direction)
    }

    @Test
    fun resolveSwipeDirection_bottomEdgeHorizontalLeft_returnsUp() {
        val strip = RectF(400f, 1934f, 600f, 2000f)
        val direction = SwipePathGeometry.resolveSwipeDirection(
            side = PanelSide.BOTTOM,
            stripBounds = strip,
            startX = 500f,
            startY = 1990f,
            fingerX = 400f,
            fingerY = 1990f,
            angle = GestureAngle.DEFAULT_BOTTOM,
        )
        assertEquals(SwipeDirection.UP, direction)
    }

    @Test
    fun classifySwipeTrigger_topEdgeHorizontalLeft_returnsShortSwipeUp() {
        val strip = RectF(400f, 0f, 600f, 66f)
        val trigger = SwipePathGeometry.classifySwipeTrigger(
            side = PanelSide.TOP,
            stripBounds = strip,
            startX = 500f,
            startY = 10f,
            fingerX = 400f,
            fingerY = 10f,
            shortThresholdPx = 60f,
            longThresholdPx = 120f,
            angle = GestureAngle.DEFAULT_TOP,
        )
        assertEquals(GestureTriggerType.SHORT_SWIPE_UP, trigger)
    }

    @Test
    fun resolveCornerSwipeTrigger_secondSegmentStillIn_returnsNull() {
        val strip = RectF(0f, 0f, 20f, 2000f)
        val trigger = SwipePathGeometry.resolveCornerSwipeTrigger(
            side = PanelSide.LEFT,
            stripBounds = strip,
            inwardReachedThreshold = true,
            currentInward = 80f,
            shortThresholdPx = 60f,
            longThresholdPx = 120f,
            gestureStartX = 0f,
            gestureStartY = 100f,
            anchorX = 60f,
            anchorY = 130f,
            fingerX = 80f,
            fingerY = 140f,
            turnThresholdPx = 32f,
            angle = GestureAngle.DEFAULT_LEFT,
        )

        assertNull(trigger)
    }

    @Test
    fun resolveCornerSwipeTrigger_overallStillIn_returnsNull() {
        val strip = RectF(0f, 0f, 20f, 2000f)
        val trigger = SwipePathGeometry.resolveCornerSwipeTrigger(
            side = PanelSide.LEFT,
            stripBounds = strip,
            inwardReachedThreshold = true,
            currentInward = 80f,
            shortThresholdPx = 60f,
            longThresholdPx = 120f,
            gestureStartX = 0f,
            gestureStartY = 100f,
            anchorX = 80f,
            anchorY = 100f,
            fingerX = 80f,
            fingerY = 140f,
            turnThresholdPx = 32f,
            angle = GestureAngle.DEFAULT_LEFT,
        )

        assertNull(trigger)
    }

    @Test
    fun resolveCornerSwipeTrigger_secondSegmentDown_returnsShortSwipeInDown() {
        val strip = RectF(0f, 0f, 20f, 2000f)
        val trigger = SwipePathGeometry.resolveCornerSwipeTrigger(
            side = PanelSide.LEFT,
            stripBounds = strip,
            inwardReachedThreshold = true,
            currentInward = 80f,
            shortThresholdPx = 60f,
            longThresholdPx = 120f,
            gestureStartX = 0f,
            gestureStartY = 100f,
            anchorX = 80f,
            anchorY = 100f,
            fingerX = 80f,
            fingerY = 170f,
            turnThresholdPx = 32f,
            angle = GestureAngle.DEFAULT_LEFT,
        )

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_DOWN, trigger)
    }

    @Test
    fun classifyOnUp_topPanelLenientTapWithHorizontalJitter_returnsSingleTap() {
        val topStrip = RectF(400f, 0f, 600f, 66f)
        val recognizer = SwipePathRecognizer(PanelSide.TOP, density = 3f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(500f, 20f, topStrip)
        val result = recognizer.classifyOnUp(
            530f,
            25f,
            SwipePathRecognizer.ClassifyOptions.LENIENT_SINGLE_TAP,
        )

        assertEquals(GestureTriggerType.SHORT_SINGLE_TAP, result?.trigger)
    }
}
