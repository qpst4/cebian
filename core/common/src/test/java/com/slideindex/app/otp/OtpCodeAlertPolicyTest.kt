package com.slideindex.app.otp

import org.junit.Assert.assertEquals
import org.junit.Test

class OtpCodeAlertPolicyTest {
    @Test
    fun normalizeRetentionSeconds_clampsOutOfRangeValues() {
        assertEquals(
            OtpCodeAlertPolicy.MIN_RETENTION_SECONDS,
            OtpCodeAlertPolicy.normalizeRetentionSeconds(-1),
        )
        assertEquals(
            OtpCodeAlertPolicy.MAX_RETENTION_SECONDS,
            OtpCodeAlertPolicy.normalizeRetentionSeconds(9999),
        )
        assertEquals(7, OtpCodeAlertPolicy.normalizeRetentionSeconds(7))
    }

    @Test
    fun retentionMillis_zeroMeansNoTimeout() {
        assertEquals(0L, OtpCodeAlertPolicy.retentionMillis(OtpCodeAlertPolicy.RETENTION_NEVER_SECONDS))
    }

    @Test
    fun retentionMillis_convertsSecondsToMillis() {
        assertEquals(5000L, OtpCodeAlertPolicy.retentionMillis(5))
    }
}
