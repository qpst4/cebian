package com.slideindex.app.ui.settings.clipboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.clipboard.ClipboardWhitelistBridge
import com.slideindex.app.clipboard.ClipboardWhitelistContract
import com.slideindex.app.clipboard.XposedServiceHolder
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardMonitoringPath
import io.github.libxposed.service.XposedService

enum class ClipboardMonitoringStatusKind {
    READ_LOGS,
    SELF_HOOK,
}

data class ClipboardMonitoringUiState(
    val lsposedServiceConnected: Boolean,
    val readLogsGranted: Boolean,
    val selfHookReady: Boolean,
    val lsposedWhitelistSynced: Boolean,
)

class ClipboardMonitoringUiStateHolder internal constructor(
    val state: ClipboardMonitoringUiState,
    internal val refreshReadLogsGranted: () -> Unit,
    internal val refreshRemoteWhitelist: () -> Unit,
)

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
 * Self-hook is ready when Xposed service is connected, LSPosed monitoring is enabled, and the
 * remote whitelist already contains this app package.
 */
fun isClipboardSelfHookReady(
    settings: AppSettings,
    lsposedServiceConnected: Boolean,
    remoteWhitelist: Set<String>?,
): Boolean {
    if (!lsposedServiceConnected || !isClipboardSelfHookEnabled(settings)) return false
    val remote = remoteWhitelist ?: return false
    return ClipboardWhitelistContract.APP_PACKAGE in remote
}

/** Whether the remote whitelist matches local settings. */
fun isLsposedWhitelistSynced(
    settings: AppSettings,
    lsposedServiceConnected: Boolean,
    remoteWhitelist: Set<String>?,
): Boolean = lsposedServiceConnected &&
    ClipboardWhitelistBridge.isRemoteWhitelistSynced(settings, remoteWhitelist)

fun computeClipboardMonitoringUiState(
    settings: AppSettings,
    lsposedServiceConnected: Boolean,
    remoteWhitelist: Set<String>?,
    readLogsGranted: Boolean,
): ClipboardMonitoringUiState = ClipboardMonitoringUiState(
    lsposedServiceConnected = lsposedServiceConnected,
    readLogsGranted = readLogsGranted,
    selfHookReady = isClipboardSelfHookReady(settings, lsposedServiceConnected, remoteWhitelist),
    lsposedWhitelistSynced = isLsposedWhitelistSynced(
        settings,
        lsposedServiceConnected,
        remoteWhitelist,
    ),
)

@Composable
fun rememberClipboardMonitoringUiState(settings: AppSettings): ClipboardMonitoringUiStateHolder {
    val context = LocalContext.current
    val currentSettings by rememberUpdatedState(settings)
    var lsposedServiceConnected by remember {
        mutableStateOf(ClipboardWhitelistBridge.isServiceConnected())
    }
    var remoteWhitelist by remember {
        mutableStateOf(ClipboardWhitelistBridge.readRemoteWhitelist())
    }
    var readLogsGranted by remember {
        mutableStateOf(ClipboardPermissionHelper.hasReadLogsPermission(context))
    }

    fun syncAndRefreshRemoteWhitelist() {
        remoteWhitelist = ClipboardWhitelistBridge.syncAndReadRemoteWhitelist(currentSettings)
    }

    fun refreshRemoteWhitelist() {
        remoteWhitelist = ClipboardWhitelistBridge.readRemoteWhitelist()
    }

    fun refreshReadLogsGranted() {
        readLogsGranted = ClipboardPermissionHelper.hasReadLogsPermission(context)
    }

    DisposableEffect(Unit) {
        val listener: (XposedService?) -> Unit = { service ->
            lsposedServiceConnected = service != null
            remoteWhitelist = if (service != null) {
                ClipboardWhitelistBridge.syncAndReadRemoteWhitelist(currentSettings)
            } else {
                null
            }
        }
        XposedServiceHolder.addListener(listener)
        onDispose { XposedServiceHolder.removeListener(listener) }
    }

    DisposableEffect(lsposedServiceConnected) {
        if (!lsposedServiceConnected) {
            onDispose { }
        } else {
            val unregister = ClipboardWhitelistBridge.registerRemoteWhitelistChangeListener {
                refreshRemoteWhitelist()
            }
            onDispose { unregister() }
        }
    }

    LaunchedEffect(
        currentSettings.clipboardBackgroundMonitoring,
        currentSettings.clipboardBackgroundMonitoringPath,
        currentSettings.clipboardLsposedExtraWhitelist,
        lsposedServiceConnected,
    ) {
        if (lsposedServiceConnected) {
            syncAndRefreshRemoteWhitelist()
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshReadLogsGranted()
                if (lsposedServiceConnected) {
                    syncAndRefreshRemoteWhitelist()
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val state = remember(settings, lsposedServiceConnected, remoteWhitelist, readLogsGranted) {
        computeClipboardMonitoringUiState(
            settings = settings,
            lsposedServiceConnected = lsposedServiceConnected,
            remoteWhitelist = remoteWhitelist,
            readLogsGranted = readLogsGranted,
        )
    }
    return ClipboardMonitoringUiStateHolder(
        state = state,
        refreshReadLogsGranted = ::refreshReadLogsGranted,
        refreshRemoteWhitelist = ::syncAndRefreshRemoteWhitelist,
    )
}
