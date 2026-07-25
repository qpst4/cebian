package com.slideindex.app.ui.settings.clipboard

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

fun isClipboardSelfHookReady(
    lsposedServiceConnected: Boolean,
    lsposedReady: Boolean,
): Boolean = lsposedServiceConnected && lsposedReady
