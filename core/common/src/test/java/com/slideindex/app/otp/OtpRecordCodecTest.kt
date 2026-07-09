package com.slideindex.app.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class OtpRecordCodecTest {

    @Test
    fun encodeDecode_roundTrip_preservesFields() {
        val original = listOf(
            OtpRecord(
                id = "otp-1",
                code = "884812",
                packageName = "com.bank.app",
                title = "银行",
                text = "验证码884812",
                timestampMs = 1_700_000_000_000L,
                ruleName = "通用中文验证码",
                isTest = true,
            ),
        )

        val decoded = OtpRecordCodec.decode(OtpRecordCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun decode_blank_returnsEmpty() {
        assertTrue(OtpRecordCodec.decode("").isEmpty())
    }

    @Test
    fun decode_skipsInvalidEntries() {
        val raw = """[{"id":"x","code":"","packageName":"com.app"}]"""
        assertTrue(OtpRecordCodec.decode(raw).isEmpty())
    }
}
