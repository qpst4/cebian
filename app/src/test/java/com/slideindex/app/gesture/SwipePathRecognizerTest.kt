package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Test

class SwipePathRecognizerTest {

    @Test
    fun classifyOnUp_leftPanelInwardSwipe_returnsShortSwipeIn() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)

        recognizer.onTouchDown(0f, 100f)
        val result = recognizer.classifyOnUp(90f, 100f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_rightPanelInwardSwipe_returnsShortSwipeIn() {
        val recognizer = SwipePathRecognizer(PanelSide.RIGHT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)

        recognizer.onTouchDown(200f, 100f)
        val result = recognizer.classifyOnUp(110f, 100f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
    }
}
