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
@Config(sdk = [Build.VERSION_CODES.R])
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
    fun formatRuntimeStatusReflectsInjectPipeline() {
        val settings = AppSettings(
            otpAutoInputEnabled = true,
            otpLsposedSystemInjectEnabled = true,
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
