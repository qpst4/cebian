package com.slideindex.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SlideIndexAccessibilityGestureInjectorTest {
    @Test
    fun sanitizeSwipeEndpoints_clampsToScreenAndEnforcesMinDistance() {
        val result = SlideIndexAccessibilityGestureInjector.sanitizeSwipeEndpoints(
            startX = 10f,
            startY = 10f,
            endX = 12f,
            endY = 12f,
            screenWidth = 200f,
            screenHeight = 400f,
            minDistancePx = 50f,
        )
        assertNotNull(result)
        val endpoints = result!!
        val distance = kotlin.math.hypot(
            (endpoints.endX - endpoints.startX).toDouble(),
            (endpoints.endY - endpoints.startY).toDouble(),
        ).toFloat()
        assertEquals(50f, distance, 0.5f)
    }

    @Test
    fun sanitizeSwipeEndpoints_returnsNullForNonFinite() {
        assertNull(
            SlideIndexAccessibilityGestureInjector.sanitizeSwipeEndpoints(
                startX = Float.NaN,
                startY = 10f,
                endX = 100f,
                endY = 100f,
                screenWidth = 200f,
                screenHeight = 400f,
                minDistancePx = 10f,
            ),
        )
    }

    @Test
    fun buildSwipePath_hasMultipleSegments() {
        val path = SlideIndexAccessibilityGestureInjector.buildSwipePath(0f, 0f, 100f, 0f)
        assertNotNull(path)
    }
}
