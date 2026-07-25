package com.slideindex.app.otp

import org.junit.Assert.assertEquals
import org.junit.Test

class OtpRecordFillStatusTest {

    @Test
    fun fromFillResult_mapsStrategies() {
        assertEquals(OtpRecordFillStatus.LSPOSED, OtpRecordFillStatus.fromFillResult(true, "system_inject"))
        assertEquals(OtpRecordFillStatus.ACCESSIBILITY, OtpRecordFillStatus.fromFillResult(true, "focused_node"))
        assertEquals(OtpRecordFillStatus.FAILED, OtpRecordFillStatus.fromFillResult(false, "system_inject"))
        assertEquals(OtpRecordFillStatus.FAILED, OtpRecordFillStatus.fromFillResult(true, "none"))
    }

    @Test
    fun storageKey_roundTrip() {
        OtpRecordFillStatus.entries.forEach { status ->
            assertEquals(status, OtpRecordFillStatus.fromStorageKey(status.storageKey()))
        }
    }
}
