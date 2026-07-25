package com.slideindex.app.clipboard

import android.util.Log
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardMonitoringPath
import io.github.libxposed.service.XposedService

object ClipboardWhitelistBridge {
  private const val TAG = "ClipboardWhitelistBridge"

  fun buildWhitelist(settings: AppSettings): Set<String> {
    val packages = settings.clipboardLsposedExtraWhitelist.toMutableSet()
    if (settings.clipboardBackgroundMonitoring &&
      settings.clipboardBackgroundMonitoringPath == ClipboardMonitoringPath.LSPOSED
    ) {
      packages += ClipboardWhitelistContract.APP_PACKAGE
    }
    return packages
  }

  fun sync(settings: AppSettings) {
    val service = XposedServiceHolder.currentService()
    if (service == null) {
      Log.w(TAG, "Xposed service unavailable, skip whitelist sync")
      return
    }
    sync(service, buildWhitelist(settings))
  }

  fun sync(service: XposedService, whitelist: Set<String>) {
    runCatching {
      service.getRemotePreferences(ClipboardWhitelistContract.REMOTE_PREFS_NAME)
        .edit()
        .putStringSet(ClipboardWhitelistContract.KEY_WHITELIST, whitelist)
        .apply()
      Log.i(TAG, "Synced clipboard whitelist (${whitelist.size} packages)")
    }.onFailure {
      Log.w(TAG, "Failed to sync clipboard whitelist", it)
    }
  }

  fun isServiceConnected(): Boolean = XposedServiceHolder.currentService() != null

  fun isReady(settings: AppSettings): Boolean =
    isServiceConnected() && buildWhitelist(settings).isNotEmpty()
}
