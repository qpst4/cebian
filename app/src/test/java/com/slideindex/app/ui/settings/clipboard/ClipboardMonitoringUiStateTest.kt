package com.slideindex.app.ui.settings.clipboard

import android.os.Build
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardMonitoringPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class ClipboardMonitoringUiStateTest {
  private val lsposedMonitoring = AppSettings(
    clipboardBackgroundMonitoring = true,
    clipboardBackgroundMonitoringPath = ClipboardMonitoringPath.LSPOSED,
  )

  @Test
  fun resolveStatusKindByMonitoringPath() {
    assertEquals(
      ClipboardMonitoringStatusKind.READ_LOGS,
      resolveClipboardMonitoringStatusKind(ClipboardMonitoringPath.LOGCAT),
    )
    assertEquals(
      ClipboardMonitoringStatusKind.SELF_HOOK,
      resolveClipboardMonitoringStatusKind(ClipboardMonitoringPath.LSPOSED),
    )
  }

  @Test
  fun selfHookReadyRequiresServiceAndLsposedMonitoring() {
    assertTrue(isClipboardSelfHookReady(lsposedMonitoring, lsposedServiceConnected = true))
    assertFalse(isClipboardSelfHookReady(lsposedMonitoring, lsposedServiceConnected = false))
    assertFalse(
      isClipboardSelfHookReady(
        lsposedMonitoring.copy(clipboardBackgroundMonitoring = false),
        lsposedServiceConnected = true,
      ),
    )
    assertFalse(
      isClipboardSelfHookReady(
        lsposedMonitoring.copy(clipboardBackgroundMonitoringPath = ClipboardMonitoringPath.LOGCAT),
        lsposedServiceConnected = true,
      ),
    )
  }

  @Test
  fun whitelistSyncedWhenConfiguredAndServiceConnected() {
    assertTrue(isLsposedWhitelistSynced(lsposedMonitoring, lsposedServiceConnected = true))
    assertFalse(isLsposedWhitelistSynced(lsposedMonitoring, lsposedServiceConnected = false))
    assertFalse(
      isLsposedWhitelistSynced(
        AppSettings(
          clipboardBackgroundMonitoring = false,
          clipboardBackgroundMonitoringPath = ClipboardMonitoringPath.LSPOSED,
        ),
        lsposedServiceConnected = true,
      ),
    )
  }
}
