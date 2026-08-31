package com.slideindex.app.shake

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ShakeGestureDetectorTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun effectiveThreshold_higherValueMeansLowerThreshold() {
        assertEquals(10f, ShakeGestureDetector.effectiveThreshold(1f))
        assertEquals(8f, ShakeGestureDetector.effectiveThreshold(3f))
        assertEquals(5f, ShakeGestureDetector.effectiveThreshold(6f))
        assertEquals(1f, ShakeGestureDetector.effectiveThreshold(10f))
    }

    @Test
    fun setSensitivity_clampsOutOfRangeValues() {
        val detector = ShakeGestureDetector(context) { }

        detector.setSensitivity(0.2f, independentEnabled = false, perDirection = emptyMap())
        detector.setSensitivity(25f, independentEnabled = false, perDirection = emptyMap())

        assertEquals(1f, ShakeGestureClassifier.clampSensitivity(0.2f))
        assertEquals(10f, ShakeGestureClassifier.clampSensitivity(25f))
    }
}
