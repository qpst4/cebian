package com.slideindex.app.clipboard

import android.os.Build
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardMonitoringPath
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class ClipboardWhitelistBridgeTest {
  @Test
  fun buildWhitelistIncludesSelfWhenLsposedMonitoringEnabled() {
    val settings = AppSettings(
      clipboardBackgroundMonitoring = true,
      clipboardBackgroundMonitoringPath = ClipboardMonitoringPath.LSPOSED,
      clipboardLsposedExtraWhitelist = setOf("com.example.app"),
    )

    val whitelist = ClipboardWhitelistBridge.buildWhitelist(settings)

    assertTrue(ClipboardWhitelistContract.APP_PACKAGE in whitelist)
    assertTrue("com.example.app" in whitelist)
  }

  @Test
  fun remoteWhitelistSyncedWhenSetsMatch() {
    val settings = AppSettings(
      clipboardBackgroundMonitoring = true,
      clipboardBackgroundMonitoringPath = ClipboardMonitoringPath.LSPOSED,
    )
    val expected = ClipboardWhitelistBridge.buildWhitelist(settings)

    assertTrue(ClipboardWhitelistBridge.isRemoteWhitelistSynced(settings, expected))
    assertFalse(ClipboardWhitelistBridge.isRemoteWhitelistSynced(settings, emptySet()))
    assertFalse(ClipboardWhitelistBridge.isRemoteWhitelistSynced(settings, null))
  }
}
