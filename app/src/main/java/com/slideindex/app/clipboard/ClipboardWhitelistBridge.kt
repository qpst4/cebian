package com.slideindex.app.clipboard

/*
 * Portions derived from Clipboard Whitelist (https://github.com/Tehcneko/ClipboardWhitelist)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.SharedPreferences
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
        .commit()
      Log.i(TAG, "Synced clipboard whitelist (${whitelist.size} packages)")
    }.onFailure {
      Log.w(TAG, "Failed to sync clipboard whitelist", it)
    }
  }

  /** Push local settings to remote prefs, then read back the stored whitelist. */
  fun syncAndReadRemoteWhitelist(settings: AppSettings): Set<String>? {
    sync(settings)
    return readRemoteWhitelist()
  }

  fun isServiceConnected(): Boolean = XposedServiceHolder.currentService() != null

  fun readRemoteWhitelist(): Set<String>? {
    val service = XposedServiceHolder.currentService() ?: return null
    return readRemoteWhitelist(service)
  }

  fun readRemoteWhitelist(service: XposedService): Set<String> =
    runCatching {
      service.getRemotePreferences(ClipboardWhitelistContract.REMOTE_PREFS_NAME)
        .getStringSet(ClipboardWhitelistContract.KEY_WHITELIST, emptySet())
        .orEmpty()
    }.getOrElse { emptySet() }

  /** Whether the remote whitelist matches what local settings would sync. */
  fun isRemoteWhitelistSynced(settings: AppSettings, remoteWhitelist: Set<String>?): Boolean {
    if (remoteWhitelist == null) return false
    return remoteWhitelist == buildWhitelist(settings)
  }

  fun isReady(settings: AppSettings): Boolean =
    isServiceConnected() && isRemoteWhitelistSynced(settings, readRemoteWhitelist())

  /**
   * Registers a listener for remote whitelist changes. Returns an unregister callback, or a no-op
   * when the Xposed service is unavailable.
   */
  fun registerRemoteWhitelistChangeListener(onChanged: () -> Unit): () -> Unit {
    val service = XposedServiceHolder.currentService() ?: return {}
    val preferences = service.getRemotePreferences(ClipboardWhitelistContract.REMOTE_PREFS_NAME)
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
      if (changedKey == null || changedKey == ClipboardWhitelistContract.KEY_WHITELIST) {
        onChanged()
      }
    }
    preferences.registerOnSharedPreferenceChangeListener(listener)
    return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
  }
}