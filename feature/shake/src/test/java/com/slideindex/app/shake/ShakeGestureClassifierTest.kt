package com.slideindex.app.shake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShakeGestureClassifierTest {

    @Test
    fun clampSensitivity_limitsToOneThroughTen() {
        assertEquals(1f, ShakeGestureClassifier.clampSensitivity(0.2f))
        assertEquals(10f, ShakeGestureClassifier.clampSensitivity(99f))
        assertEquals(6f, ShakeGestureClassifier.clampSensitivity(6f))
    }

    @Test
    fun effectiveThreshold_higherValueMeansLowerThreshold() {
        assertEquals(10f, ShakeGestureClassifier.effectiveThreshold(1f))
        assertEquals(8f, ShakeGestureClassifier.effectiveThreshold(3f))
        assertEquals(5f, ShakeGestureClassifier.effectiveThreshold(6f))
        assertEquals(1f, ShakeGestureClassifier.effectiveThreshold(10f))
    }

    @Test
    fun detectDirection_rightFlip_whenAxisYExceedsThreshold() {
        val direction = ShakeGestureClassifier.detectDirection(
            axisX = 0f,
            axisY = 8f,
            axisZ = 0f,
            absX = 0f,
            absY = 8f,
            absZ = 0f,
            globalSensitivity = 6f,
            independentEnabled = false,
            perDirectionSensitivity = emptyMap(),
        )

        assertEquals(ShakeGestureType.RIGHT_FLIP, direction)
    }

    @Test
    fun detectDirection_leftFlip_whenAxisYNegativeExceedsThreshold() {
        val direction = ShakeGestureClassifier.detectDirection(
            axisX = 0f,
            axisY = -8f,
            axisZ = 0f,
            absX = 0f,
            absY = 8f,
            absZ = 0f,
            globalSensitivity = 6f,
            independentEnabled = false,
            perDirectionSensitivity = emptyMap(),
        )

        assertEquals(ShakeGestureType.LEFT_FLIP, direction)
    }

    @Test
    fun detectDirection_returnsNullWhenBelowThreshold() {
        val direction = ShakeGestureClassifier.detectDirection(
            axisX = 0.5f,
            axisY = 0.5f,
            axisZ = 0.5f,
            absX = 0.5f,
            absY = 0.5f,
            absZ = 0.5f,
            globalSensitivity = 6f,
            independentEnabled = false,
            perDirectionSensitivity = emptyMap(),
        )

        assertNull(direction)
    }

    @Test
    fun detectDirection_independentMode_emptyPerDirection_usesEffectiveThreshold() {
        val direction = ShakeGestureClassifier.detectDirection(
            axisX = 0f,
            axisY = 5f,
            axisZ = 0f,
            absX = 0f,
            absY = 5f,
            absZ = 0f,
            globalSensitivity = 3f,
            independentEnabled = true,
            perDirectionSensitivity = emptyMap(),
        )

        assertNull(direction)
    }

    @Test
    fun detectDirection_independentMode_minimumSensitivityIsHardest() {
        val atMin = ShakeGestureClassifier.detectDirection(
            axisX = 0f,
            axisY = 9f,
            axisZ = 0f,
            absX = 0f,
            absY = 9f,
            absZ = 0f,
            globalSensitivity = 1f,
            independentEnabled = true,
            perDirectionSensitivity = mapOf(ShakeGestureType.RIGHT_FLIP to 1f),
        )

        assertNull(atMin)

        val atMax = ShakeGestureClassifier.detectDirection(
            axisX = 0f,
            axisY = 2.6f,
            axisZ = 0f,
            absX = 0f,
            absY = 2.6f,
            absZ = 0f,
            globalSensitivity = 10f,
            independentEnabled = true,
            perDirectionSensitivity = mapOf(ShakeGestureType.RIGHT_FLIP to 10f),
        )

        assertEquals(ShakeGestureType.RIGHT_FLIP, atMax)
    }
}
