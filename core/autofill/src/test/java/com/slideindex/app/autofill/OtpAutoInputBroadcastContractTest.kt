package com.slideindex.app.autofill

import android.content.Intent
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
class OtpAutoInputBroadcastContractTest {
    @Test
    fun buildRequestIntent_roundTripsThroughReadRequest() {
        val request = OtpAutoInputBroadcastContract.Request(
            code = "123456",
            autoEnter = true,
            inputIntervalMs = 80L,
            attemptId = 42L,
            allowSystemInject = false,
        )

        val parsed = OtpAutoInputBroadcastContract.readRequest(
            OtpAutoInputBroadcastContract.buildRequestIntent(request),
        )

        assertEquals(request, parsed)
    }

    @Test
    fun readRequest_returnsNullForMissingOrEmptyCode() {
        assertNull(OtpAutoInputBroadcastContract.readRequest(Intent(OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT)))
        assertNull(
            OtpAutoInputBroadcastContract.readRequest(
                Intent(OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT).apply {
                    putExtra(OtpAutoInputBroadcastContract.EXTRA_CODE, "")
                },
            ),
        )
    }

    @Test
    fun buildProbeIntent_marksProbeAttempt() {
        val intent = OtpAutoInputBroadcastContract.buildProbeIntent(99L)

        assertTrue(intent.getBooleanExtra(OtpAutoInputBroadcastContract.EXTRA_PROBE, false))
        assertEquals(99L, intent.getLongExtra(OtpAutoInputBroadcastContract.EXTRA_ATTEMPT_ID, -1L))
        assertNull(OtpAutoInputBroadcastContract.readRequest(intent))
    }

    @Test
    fun buildResultIntent_scopesToModulePackage() {
        val intent = OtpAutoInputBroadcastContract.buildResultIntent(
            attemptId = 7L,
            success = true,
            strategy = "focused_node",
            reason = "ok",
        )

        assertEquals(OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT_RESULT, intent.action)
        assertEquals("com.slideindex.app", intent.`package`)
        assertTrue(intent.getBooleanExtra(OtpAutoInputBroadcastContract.EXTRA_SUCCESS, false))
        assertEquals("focused_node", intent.getStringExtra(OtpAutoInputBroadcastContract.EXTRA_STRATEGY))
        assertEquals("ok", intent.getStringExtra(OtpAutoInputBroadcastContract.EXTRA_REASON))
        assertFalse(intent.hasExtra(OtpAutoInputBroadcastContract.EXTRA_PROBE))
    }
}
