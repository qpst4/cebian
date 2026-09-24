package com.slideindex.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeThrottleTest {

    @Test
    fun firstCallIsDue() {
        val throttle = NudgeThrottle(minIntervalMs = 60_000L)
        assertTrue(throttle.beginIfDue(nowMs = 1_000L))
    }

    @Test
    fun secondCallInsideIntervalIsSkipped() {
        val throttle = NudgeThrottle(minIntervalMs = 60_000L)
        assertTrue(throttle.beginIfDue(nowMs = 1_000L))
        assertFalse(throttle.beginIfDue(nowMs = 30_000L))
        assertFalse(throttle.beginIfDue(nowMs = 60_999L))
    }

    @Test
    fun callAfterIntervalIsDueAgain() {
        val throttle = NudgeThrottle(minIntervalMs = 60_000L)
        assertTrue(throttle.beginIfDue(nowMs = 1_000L))
        assertTrue(throttle.beginIfDue(nowMs = 61_000L))
    }

    @Test
    fun resetMakesNextCallDue() {
        val throttle = NudgeThrottle(minIntervalMs = 60_000L)
        assertTrue(throttle.beginIfDue(nowMs = 1_000L))
        throttle.reset()
        assertTrue(throttle.beginIfDue(nowMs = 1_001L))
    }
}
