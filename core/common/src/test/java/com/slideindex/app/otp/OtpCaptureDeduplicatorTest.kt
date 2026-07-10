package com.slideindex.app.otp

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpCaptureDeduplicatorTest {
    @After
    fun tearDown() {
        OtpCaptureDeduplicator.clearForTests()
    }

    @Test
    fun tryConsumeSmsForward_rejectsDuplicateBodyWithinWindow() {
        assertTrue(OtpCaptureDeduplicator.tryConsumeSmsForward("+8613800138000", "code 123456"))
        assertFalse(OtpCaptureDeduplicator.tryConsumeSmsForward("+8613800138000", "code 123456"))
    }

    @Test
    fun tryConsumeExtractedCode_rejectsDuplicateCodeRegardlessOfSource() {
        assertTrue(OtpCaptureDeduplicator.tryConsumeExtractedCode("123456"))
        assertFalse(OtpCaptureDeduplicator.tryConsumeExtractedCode("123456"))
    }

    @Test
    fun tryConsumeAutoFillRequest_rejectsDuplicateFillWithinWindow() {
        assertTrue(OtpCaptureDeduplicator.tryConsumeAutoFillRequest("654321"))
        assertFalse(OtpCaptureDeduplicator.tryConsumeAutoFillRequest("654321"))
    }
}
