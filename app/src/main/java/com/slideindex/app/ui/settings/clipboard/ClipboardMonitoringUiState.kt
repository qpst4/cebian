package com.slideindex.app.ui.settings.clipboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slideindex.app.clipboard.monitor.ClipboardMonitorController
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardMonitoringMode
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.util.PermissionHelper
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

data class ClipboardMonitoringUiState(
    val shizukuGranted: Boolean,
    val rootAvailable: Boolean,
    val overlayGranted: Boolean,
    val monitorRunning: Boolean,
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ClipboardMonitorControllerEntryPoint {
    fun clipboardMonitorController(): ClipboardMonitorController
}

@Composable
fun rememberClipboardMonitoringUiState(settings: AppSettings): ClipboardMonitoringUiState {
    val context = LocalContext.current
    val controller = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ClipboardMonitorControllerEntryPoint::class.java,
        ).clipboardMonitorController()
    }
    var shizukuGranted by remember { mutableStateOf(controller.hasShizukuPermission()) }
    var rootAvailable by remember { mutableStateOf(controller.isRootAvailable()) }
    var overlayGranted by remember {
        mutableStateOf(PermissionHelper.canDrawOverlays(context))
    }
    var monitorRunning by remember { mutableStateOf(controller.isListening) }

    fun refresh() {
        shizukuGranted = controller.hasShizukuPermission()
        rootAvailable = controller.isRootAvailable()
        overlayGranted = PermissionHelper.canDrawOverlays(context)
        monitorRunning = controller.isListening
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, settings.clipboardBackgroundMonitoring) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    return remember(shizukuGranted, rootAvailable, overlayGranted, monitorRunning, settings) {
        ClipboardMonitoringUiState(
            shizukuGranted = shizukuGranted,
            rootAvailable = rootAvailable,
            overlayGranted = overlayGranted,
            monitorRunning = monitorRunning,
        )
    }
}

fun isClipboardMonitoringBackendReady(
    mode: ClipboardMonitoringMode,
    privilegeMode: PrivilegeMode,
    state: ClipboardMonitoringUiState,
): Boolean {
    val effective = mode.effective(privilegeMode)
    return when {
        effective.usesStandardApi -> true
        effective.usesRoot -> state.rootAvailable
        else -> state.shizukuGranted
    } && (effective.usesStandardApi || state.overlayGranted)
}

fun AppSettings.isClipboardMonitoringBackendReady(state: ClipboardMonitoringUiState): Boolean =
    isClipboardMonitoringBackendReady(
        mode = clipboardBackgroundMonitoringMode,
        privilegeMode = privilegeMode,
        state = state,
    )
