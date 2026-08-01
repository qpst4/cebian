package com.slideindex.app.gesture

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SwipePathRecognizerTest {

    private val leftStrip = RectF(0f, 0f, 20f, 2000f)
    private val rightStrip = RectF(180f, 0f, 200f, 2000f)

    @Test
    fun classifyOnUp_leftPanelInwardSwipe_returnsShortSwipeIn() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(0f, 100f, leftStrip)
        val result = recognizer.classifyOnUp(90f, 100f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_rightPanelInwardSwipe_returnsShortSwipeIn() {
        val recognizer = SwipePathRecognizer(PanelSide.RIGHT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(195f, 100f, rightStrip)
        val result = recognizer.classifyOnUp(130f, 100f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_topPanelLenientTapWithSlightInwardMove_returnsSingleTap() {
        val topStrip = RectF(400f, 0f, 600f, 66f)
        val recognizer = SwipePathRecognizer(PanelSide.TOP, density = 3f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(500f, 10f, topStrip)
        val result = recognizer.classifyOnUp(
            500f,
            55f,
            SwipePathRecognizer.ClassifyOptions.LENIENT_SINGLE_TAP,
        )

        assertEquals(GestureTriggerType.SHORT_SINGLE_TAP, result?.trigger)
    }
}
