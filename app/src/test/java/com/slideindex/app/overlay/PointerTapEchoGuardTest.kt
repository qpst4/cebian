package com.slideindex.app.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerTapEchoGuardTest {

    private val guard = PointerTapEchoGuard()

    @Test
    fun armSetsActiveUntilReset() {
        guard.arm(100f, 200f, echoSlopPx = 32f)
        assertTrue(guard.isActive)
        guard.reset()
        assertFalse(guard.isActive)
    }
}
