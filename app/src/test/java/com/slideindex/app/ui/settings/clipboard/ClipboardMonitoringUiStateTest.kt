package com.slideindex.app.ui.settings.clipboard



import android.os.Build

import com.slideindex.app.clipboard.ClipboardWhitelistBridge

import com.slideindex.app.clipboard.ClipboardWhitelistContract

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



  private val expectedLsposedWhitelist = ClipboardWhitelistBridge.buildWhitelist(lsposedMonitoring)



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

  fun selfHookReadyRequiresServiceRemoteWhitelistAndLsposedMonitoring() {

    assertTrue(

      isClipboardSelfHookReady(

        lsposedMonitoring,

        lsposedServiceConnected = true,

        remoteWhitelist = expectedLsposedWhitelist,

      ),

    )

    assertFalse(

      isClipboardSelfHookReady(

        lsposedMonitoring,

        lsposedServiceConnected = false,

        remoteWhitelist = expectedLsposedWhitelist,

      ),

    )

    assertFalse(

      isClipboardSelfHookReady(

        lsposedMonitoring,

        lsposedServiceConnected = true,

        remoteWhitelist = null,

      ),

    )

    assertFalse(

      isClipboardSelfHookReady(

        lsposedMonitoring,

        lsposedServiceConnected = true,

        remoteWhitelist = emptySet(),

      ),

    )

    assertFalse(

      isClipboardSelfHookReady(

        lsposedMonitoring.copy(clipboardBackgroundMonitoring = false),

        lsposedServiceConnected = true,

        remoteWhitelist = expectedLsposedWhitelist,

      ),

    )

    assertFalse(

      isClipboardSelfHookReady(

        lsposedMonitoring.copy(clipboardBackgroundMonitoringPath = ClipboardMonitoringPath.LOGCAT),

        lsposedServiceConnected = true,

        remoteWhitelist = expectedLsposedWhitelist,

      ),

    )

  }



  @Test

  fun whitelistSyncedWhenRemoteMatchesExpected() {

    assertTrue(

      isLsposedWhitelistSynced(

        lsposedMonitoring,

        lsposedServiceConnected = true,

        remoteWhitelist = expectedLsposedWhitelist,

      ),

    )

    assertFalse(

      isLsposedWhitelistSynced(

        lsposedMonitoring,

        lsposedServiceConnected = false,

        remoteWhitelist = expectedLsposedWhitelist,

      ),

    )

    assertFalse(

      isLsposedWhitelistSynced(

        lsposedMonitoring,

        lsposedServiceConnected = true,

        remoteWhitelist = null,

      ),

    )

    assertFalse(

      isLsposedWhitelistSynced(

        lsposedMonitoring,

        lsposedServiceConnected = true,

        remoteWhitelist = emptySet(),

      ),

    )

    assertFalse(

      isLsposedWhitelistSynced(

        AppSettings(

          clipboardBackgroundMonitoring = false,

          clipboardBackgroundMonitoringPath = ClipboardMonitoringPath.LSPOSED,

        ),

        lsposedServiceConnected = true,

        remoteWhitelist = setOf(ClipboardWhitelistContract.APP_PACKAGE),

      ),

    )

  }



  @Test

  fun computeClipboardMonitoringUiStateAggregatesFields() {

    val state = computeClipboardMonitoringUiState(

      settings = lsposedMonitoring,

      lsposedServiceConnected = true,

      remoteWhitelist = expectedLsposedWhitelist,

      readLogsGranted = false,

    )



    assertTrue(state.lsposedServiceConnected)

    assertFalse(state.readLogsGranted)

    assertTrue(state.selfHookReady)

    assertTrue(state.lsposedWhitelistSynced)

  }

}

