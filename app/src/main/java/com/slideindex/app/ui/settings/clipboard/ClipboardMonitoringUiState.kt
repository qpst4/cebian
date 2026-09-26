package com.slideindex.app.ui.settings.clipboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.clipboard.monitor.ClipboardMonitorController
import com.slideindex.app.clipboard.monitor.ClipboardMonitorStatusPort
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardMonitoringMode
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.AppProcess
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

data class ClipboardMonitoringUiState(
    val shizukuGranted: Boolean,
    val rootAvailable: Boolean,
    val overlayGranted: Boolean,
    val monitorRunning: Boolean,
    /** 当前实际在跑的监听模式；未在监听时为 null。 */
    val activeMode: ClipboardMonitoringMode? = null,
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ClipboardMonitorControllerEntryPoint {
    fun clipboardMonitorController(): ClipboardMonitorController
}

/** 状态轮询间隔：只做兜底，1s 足够，也不至于浪费电。 */
private const val STATUS_POLL_INTERVAL_MS = 1_000L

@Composable
fun rememberClipboardMonitoringUiState(settings: AppSettings): ClipboardMonitoringUiState {
    val context = LocalContext.current
    val controller = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ClipboardMonitorControllerEntryPoint::class.java,
        ).clipboardMonitorController()
    }
    // 监听状态直接跟着 controller 的 StateFlow 走：切换通道后状态行立刻更新，不必等页面 resume。
    val localRunning by controller.isListeningFlow.collectAsStateWithLifecycle()
    val localMode by controller.activeModeFlow.collectAsStateWithLifecycle()
    // 监听服务跑在 :clipboard 进程，设置页在主进程：优先用跨进程镜像，
    // 没收到镜像时（例如就在监听进程内）再回退到本进程 controller。
    //
    // 状态只有一个槽：推送（订阅）与轮询都往同一个槽写"当前值"，
    // 所以不会出现"旧值优先"——两条路径写进去的都是同一份镜像的当前内容，
    // 推送负责即时、轮询负责兜底（切换模式时主线程最忙，推送最容易晚到）。
    var liveStatus by remember { mutableStateOf(ClipboardMonitorStatusPort.status.value) }
    LaunchedEffect(Unit) {
        ClipboardMonitorStatusPort.status.collect { liveStatus = it }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val latest = ClipboardMonitorStatusPort.status.value
            if (latest != liveStatus) liveStatus = latest
            kotlinx.coroutines.delay(STATUS_POLL_INTERVAL_MS)
        }
    }
    // 切通道/切采集方式时立刻要一帧，缩短"刚切换完还显示旧状态"的窗口。
    LaunchedEffect(settings.clipboardMonitoringChannel, settings.clipboardMonitoringCapture) {
        ClipboardMonitorStatusPort.requestStatus(context)
    }
    val monitorRunning = liveStatus?.listening ?: localRunning
    val activeMode = liveStatus?.mode ?: localMode
    var shizukuGranted by remember { mutableStateOf(controller.hasShizukuPermission()) }
    var rootAvailable by remember { mutableStateOf(controller.isRootAvailable()) }
    var overlayGranted by remember {
        mutableStateOf(PermissionHelper.canDrawOverlays(context))
    }

    fun refresh() {
        shizukuGranted = controller.hasShizukuPermission()
        rootAvailable = controller.isRootAvailable()
        overlayGranted = PermissionHelper.canDrawOverlays(context)
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, settings.clipboardBackgroundMonitoring) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
                ClipboardMonitorStatusPort.requestStatus(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // 首帧也主动要一次状态，避免刚进页面时状态还是空的。
    DisposableEffect(Unit) {
        if (!AppProcess.isClipboardMonitor) {
            ClipboardMonitorStatusPort.requestStatus(context)
        }
        onDispose { }
    }

    return remember(shizukuGranted, rootAvailable, overlayGranted, monitorRunning, activeMode, settings) {
        ClipboardMonitoringUiState(
            shizukuGranted = shizukuGranted,
            rootAvailable = rootAvailable,
            overlayGranted = overlayGranted,
            monitorRunning = monitorRunning,
            activeMode = activeMode,
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
