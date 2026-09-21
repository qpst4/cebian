package com.slideindex.app.otp

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class OtpOfficialRulesLoaderTest {
    @Test
    fun getRules_loadsBundledAsset() {
        val context = RuntimeEnvironment.getApplication()
        val loader = OtpOfficialRulesLoader(context)
        assertTrue(loader.getRules().isNotEmpty())
    }
}