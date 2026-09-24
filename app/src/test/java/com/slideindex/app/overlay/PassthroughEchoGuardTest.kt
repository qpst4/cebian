package com.slideindex.app.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassthroughEchoGuardTest {

    private val guard = PassthroughEchoGuard()

    @Test
    fun notArmedNeverSwallows() {
        assertFalse(guard.isEcho(100f, 200f, nowMs = 0L))
    }

    @Test
    fun echoAtSamePointInsideWindowIsSwallowed() {
        guard.arm(1050f, 1594f, nowMs = 1_000L, durationMs = 700L)
        assertTrue(guard.isEcho(1050f, 1594f, nowMs = 1_200L))
    }

    @Test
    fun echoWithinSlopIsSwallowed() {
        guard.arm(1050f, 1594f, nowMs = 1_000L, durationMs = 700L)
        assertTrue(guard.isEcho(1070f, 1610f, nowMs = 1_100L))
    }

    @Test
    fun farTouchIsNotSwallowed() {
        guard.arm(1050f, 1594f, nowMs = 1_000L, durationMs = 700L)
        assertFalse(guard.isEcho(1050f, 1100f, nowMs = 1_100L))
    }

    @Test
    fun windowExpiryDisarmsGuard() {
        guard.arm(1050f, 1594f, nowMs = 1_000L, durationMs = 700L)
        assertFalse(guard.isEcho(1050f, 1594f, nowMs = 1_700L))
        assertFalse(guard.isEcho(1050f, 1594f, nowMs = 1_701L))
    }

    @Test
    fun resetDisarmsGuard() {
        guard.arm(1050f, 1594f, nowMs = 1_000L, durationMs = 700L)
        guard.reset()
        assertFalse(guard.isEcho(1050f, 1594f, nowMs = 1_100L))
    }
}
