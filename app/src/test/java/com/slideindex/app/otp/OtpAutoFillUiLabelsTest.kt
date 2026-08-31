package com.slideindex.app.otp

import android.os.Build
import org.robolectric.RuntimeEnvironment
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class OtpAutoFillUiLabelsTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun formatStrategyLocalizesKnownCodes() {
        assertEquals(
            context.getString(R.string.otp_autofill_strategy_system_inject),
            OtpAutoFillUiLabels.formatStrategy(context, "system_inject"),
        )
        assertEquals(
            context.getString(R.string.otp_autofill_strategy_none),
            OtpAutoFillUiLabels.formatStrategy(context, "none"),
        )
    }

    @Test
    fun formatReasonLocalizesTimeout() {
        assertEquals(
            context.getString(R.string.otp_autofill_reason_timeout),
            OtpAutoFillUiLabels.formatReason(context, "timeout"),
        )
    }

    @Test
    fun formatReasonLocalizesSystemInjectFailures() {
        assertEquals(
            context.getString(R.string.otp_autofill_reason_uid_rejected),
            OtpAutoFillUiLabels.formatReason(
                context,
                com.slideindex.app.autofill.OtpAutoInputBroadcastContract.SystemInjectReason.UID_REJECTED,
            ),
        )
        assertEquals(
            context.getString(R.string.otp_autofill_reason_inject_disabled),
            OtpAutoFillUiLabels.formatReason(
                context,
                com.slideindex.app.autofill.OtpAutoInputBroadcastContract.SystemInjectReason.INJECT_DISABLED,
            ),
        )
        assertEquals(
            context.getString(R.string.otp_autofill_reason_invalid_request),
            OtpAutoFillUiLabels.formatReason(
                context,
                com.slideindex.app.autofill.OtpAutoInputBroadcastContract.SystemInjectReason.INVALID_REQUEST,
            ),
        )
    }

    @Test
    fun formatRuntimeStatusReflectsInjectPipeline() {
        val settings = AppSettings(
            otp = com.slideindex.app.settings.OtpSettings(
                otpAutoInputEnabled = true,
                otpLsposedSystemInjectEnabled = true,
            ),
        )
        val status = OtpAutoFillUiLabels.formatRuntimeStatus(context, settings, accessibilityGranted = true)
        assertTrue(status.contains("LSPosed"))
    }

    @Test
    fun formatStatsEntrySubtitleWhenEmpty() {
        assertEquals(
            context.getString(R.string.otp_autofill_stats_entry_empty),
            OtpAutoFillUiLabels.formatStatsEntrySubtitle(context, OtpAutoFillStats()),
        )
    }
}
