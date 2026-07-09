package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
