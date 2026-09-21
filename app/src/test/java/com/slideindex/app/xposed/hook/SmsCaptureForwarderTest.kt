package com.slideindex.app.xposed.hook

import android.content.ContentValues
import android.net.Uri
import android.provider.Telephony
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SmsCaptureForwarderTest {
    @Test
    fun isSmsUri_detectsTelephonyAndSmsAuthorities() {
        assertTrue(SmsCaptureForwarder.isSmsUri(Uri.parse("content://sms/inbox")))
        assertTrue(SmsCaptureForwarder.isSmsUri(Uri.parse("content://mms-sms/conversations")))
        assertTrue(SmsCaptureForwarder.isSmsUri(Uri.parse("content://com.android.providers.telephony/sms")))
        assertFalse(SmsCaptureForwarder.isSmsUri(Uri.parse("content://contacts/people")))
        assertFalse(SmsCaptureForwarder.isSmsUri(null))
    }

    @Test
    fun readBody_prefersTelephonyColumnThenFallback() {
        val telephonyBody = ContentValues().apply {
            put(Telephony.Sms.BODY, "验证码 1234")
        }
        val fallbackBody = ContentValues().apply {
            put("body", "fallback body")
        }

        assertEquals("验证码 1234", SmsCaptureForwarder.readBody(telephonyBody))
        assertEquals("fallback body", SmsCaptureForwarder.readBody(fallbackBody))
        assertNull(SmsCaptureForwarder.readBody(null))
    }

    @Test
    fun readAddress_prefersTelephonyColumnThenFallback() {
        val telephonyAddress = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, "+8610000")
        }
        val fallbackAddress = ContentValues().apply {
            put("address", "10086")
        }

        assertEquals("+8610000", SmsCaptureForwarder.readAddress(telephonyAddress))
        assertEquals("10086", SmsCaptureForwarder.readAddress(fallbackAddress))
        assertEquals("", SmsCaptureForwarder.readAddress(null))
    }
}
