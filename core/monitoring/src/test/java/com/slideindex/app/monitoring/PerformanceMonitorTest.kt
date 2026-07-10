package com.slideindex.app.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PerformanceMonitorTest {
    @Before
    fun setUp() {
        PerformanceMonitor.resetForTesting()
    }

    @Test
    fun onFrameRateReport_updatesLatestStats() {
        val snapshot = PerformanceStatsSnapshot(fps = 58, jankFrames = 2, windowMs = 5_000)
        PerformanceMonitor.instance.onFrameRateReport(snapshot)
        assertEquals(snapshot, PerformanceMonitor.instance.latestStats)
    }

    @Test
    fun setEnabled_isIdempotentWhenDisabled() {
        PerformanceMonitor.setEnabled(false)
        PerformanceMonitor.setEnabled(false)
        assertFalse(PerformanceMonitor.instance.enabled)
    }

    @Test
    fun setEnabled_togglesState() {
        PerformanceMonitor.setEnabled(true)
        assertTrue(PerformanceMonitor.instance.enabled)
        PerformanceMonitor.setEnabled(false)
        assertFalse(PerformanceMonitor.instance.enabled)
    }

    @Test
    fun acquireOverlay_withoutUserPreference_staysDisabled() {
        PerformanceMonitor.acquireOverlay()
        assertFalse(PerformanceMonitor.instance.enabled)
    }

    @Test
    fun userPreferenceAndAcquire_enablesMonitor() {
        PerformanceMonitor.setUserPreference(true)
        PerformanceMonitor.acquireOverlay()
        assertTrue(PerformanceMonitor.instance.enabled)
    }

    @Test
    fun releaseOverlay_afterAcquire_disablesMonitor() {
        PerformanceMonitor.setUserPreference(true)
        PerformanceMonitor.acquireOverlay()
        PerformanceMonitor.releaseOverlay()
        assertFalse(PerformanceMonitor.instance.enabled)
    }

    @Test
    fun doubleRelease_doesNotThrowAndStaysDisabled() {
        PerformanceMonitor.setUserPreference(true)
        PerformanceMonitor.acquireOverlay()
        PerformanceMonitor.releaseOverlay()
        PerformanceMonitor.releaseOverlay()
        assertFalse(PerformanceMonitor.instance.enabled)
    }

    @Test
    fun userPreferenceOff_whileOverlayActive_disablesMonitor() {
        PerformanceMonitor.setUserPreference(true)
        PerformanceMonitor.acquireOverlay()
        PerformanceMonitor.setUserPreference(false)
        assertFalse(PerformanceMonitor.instance.enabled)
    }
}
