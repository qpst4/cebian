package com.slideindex.app.otp

import android.provider.Settings
import com.slideindex.app.service.SlideIndexAccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class OtpAccessibilitySettingsHelperTest {
    @Test
    fun migrateLegacyDedicatedServiceIfNeeded_replacesLegacyOtpServiceWithMainService() {
        val context = RuntimeEnvironment.getApplication()
        val mainService = "${context.packageName}/${SlideIndexAccessibilityService::class.java.name}"
        val legacyService = "com.slideindex.app/com.slideindex.app.service.OtpAutoInputAccessibilityService"
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            legacyService,
        )

        OtpAccessibilitySettingsHelper.migrateLegacyDedicatedServiceIfNeeded(context)

        val updated = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        assertFalse(updated.contains("OtpAutoInputAccessibilityService", ignoreCase = true))
        assertTrue(updated.contains(mainService, ignoreCase = true))
        assertEquals(1, Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED))
    }

    @Test
    fun migrateLegacyDedicatedServiceIfNeeded_noOpWhenLegacyMissing() {
        val context = RuntimeEnvironment.getApplication()
        val mainService = "${context.packageName}/${SlideIndexAccessibilityService::class.java.name}"
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            mainService,
        )

        OtpAccessibilitySettingsHelper.migrateLegacyDedicatedServiceIfNeeded(context)

        val updated = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        assertEquals(mainService, updated)
    }
}
