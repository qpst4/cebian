package com.slideindex.app.ui.settings.clipboard

import com.slideindex.app.clipboard.ClipboardWhitelistBridge
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardMonitoringPath

enum class ClipboardMonitoringStatusKind {
    READ_LOGS,
    SELF_HOOK,
}

fun resolveClipboardMonitoringStatusKind(
    path: ClipboardMonitoringPath,
): ClipboardMonitoringStatusKind = when (path) {
    ClipboardMonitoringPath.LOGCAT -> ClipboardMonitoringStatusKind.READ_LOGS
    ClipboardMonitoringPath.LSPOSED -> ClipboardMonitoringStatusKind.SELF_HOOK
}

/** LSPosed path with background monitoring enabled for this app. */
fun isClipboardSelfHookEnabled(settings: AppSettings): Boolean =
    settings.clipboardBackgroundMonitoring &&
        settings.clipboardBackgroundMonitoringPath == ClipboardMonitoringPath.LSPOSED

/**
 * Self-hook is ready when Xposed service is connected and the user has enabled LSPosed
 * background monitoring. Whitelist sync is triggered automatically on settings changes.
 */
fun isClipboardSelfHookReady(
    settings: AppSettings,
    lsposedServiceConnected: Boolean,
): Boolean = lsposedServiceConnected && isClipboardSelfHookEnabled(settings)

/** Whether the remote whitelist should contain at least one package. */
fun isLsposedWhitelistConfigured(settings: AppSettings): Boolean =
    ClipboardWhitelistBridge.buildWhitelist(settings).isNotEmpty()

fun isLsposedWhitelistSynced(
    settings: AppSettings,
    lsposedServiceConnected: Boolean,
): Boolean = lsposedServiceConnected && isLsposedWhitelistConfigured(settings)
