package com.slideindex.app.clipboard.monitor

/**
 * Based on [ClipboardListener](https://github.com/aa2013/ClipboardListener) (MIT).
 */
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.slideindex.app.clipboard.ClipboardPayload
import com.slideindex.app.settings.ClipboardMonitoringMode
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.util.TaskManagerUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

@Singleton
class ClipboardMonitorController @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    val config = ClipboardMonitorConfig().apply {
        applicationId = appContext.packageName
    }

    private val _isListening = MutableStateFlow(false)

    /** 监听是否处于运行状态（供设置页实时展示）。 */
    val isListeningFlow: StateFlow<Boolean> = _isListening.asStateFlow()

    val isListening: Boolean
        get() = _isListening.value

    private val _activeMode = MutableStateFlow<ClipboardMonitoringMode?>(null)

    /** 当前实际在跑的监听模式（供设置页实时展示）。 */
    val activeModeFlow: StateFlow<ClipboardMonitoringMode?> = _activeMode.asStateFlow()

    /** 当前实际在跑的监听模式（未在监听时为 null）。 */
    val activeModeOrNull: ClipboardMonitoringMode?
        get() = _activeMode.value

    var listeningServiceArgs: Shizuku.UserServiceArgs? = null
        private set

    var listeningServiceConnection: ServiceConnection? = null

    var onPayloadCaptured: ((ClipboardPayload) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingMode: ClipboardMonitoringMode? = null

    private val dispatchStartRunnable = Runnable {
        val mode = pendingMode ?: return@Runnable
        pendingMode = null
        unbindListeningService()
        markListening(false)
        _activeMode.value = mode
        startForegroundServiceInternal(mode)
    }

    init {
        listeningServiceArgs = Shizuku.UserServiceArgs(
            ComponentName(
                appContext.packageName,
                ClipboardMonitorUserService::class.java.name,
            ),
        ).daemon(false).processNameSuffix("clipboard-monitor")
        bindInstance(this)
    }

    fun startIfNeeded(mode: ClipboardMonitoringMode): Boolean {
        if (isListening && activeModeOrNull == mode) return true
        return restart(mode)
    }

    fun restart(mode: ClipboardMonitoringMode): Boolean {
        if (!ClipboardMonitorProcess.isMonitorProcess()) {
            Log.w(TAG, "skip clipboard monitor outside :overlay")
            return false
        }
        if (!canStart(mode)) {
            Log.w(TAG, "backend unavailable for mode=$mode, stopping listener")
            stop()
            return false
        }
        pendingMode = mode
        ClipboardMonitorStartup.runOnMainWhenCalm {
            mainHandler.removeCallbacks(dispatchStartRunnable)
            mainHandler.post(dispatchStartRunnable)
        }
        return true
    }

    fun start(mode: ClipboardMonitoringMode): Boolean {
        if (!ClipboardMonitorProcess.isMonitorProcess()) {
            Log.w(TAG, "skip clipboard monitor outside :overlay")
            return false
        }
        if (isListening && activeModeOrNull == mode) return true
        if (!canStart(mode)) return false
        pendingMode = mode
        // 不等主线程 idle：开机时 idle 会被启动期重活拖后，前台服务可能来不及 startForeground。
        // 现在改为「等主线程不忙」：既避免被启动期重活挤掉 startForeground 窗口，
        // 也不至于像纯 idle 那样在事件风暴下永远等不到。
        ClipboardMonitorStartup.runOnMainWhenCalm {
            mainHandler.removeCallbacks(dispatchStartRunnable)
            mainHandler.post(dispatchStartRunnable)
        }
        return true
    }

    private fun canStart(mode: ClipboardMonitoringMode): Boolean {
        // 标准 API 与 LSPosed 白名单都不依赖 Shizuku / Root。
        if (mode.usesStandardApi || mode.usesLsposed) return true
        if (mode.usesRoot) {
            if (!isRootAvailable()) {
                Log.w(TAG, "root unavailable")
                return false
            }
        } else if (!hasShizukuPermission()) {
            Log.w(TAG, "shizuku unavailable")
            return false
        }
        return true
    }

    /** 该模式此刻能不能起（供模式优先级解析用）。 */
    fun isBackendAvailable(mode: ClipboardMonitoringMode): Boolean = canStart(mode)

    private fun startForegroundServiceInternal(mode: ClipboardMonitoringMode) {
        if (!canStart(mode)) return
        val intent = Intent(appContext, ClipboardMonitorForegroundService::class.java).apply {
            putExtra(ClipboardMonitorForegroundService.EXTRA_USE_STANDARD, mode.usesStandardApi)
            putExtra(ClipboardMonitorForegroundService.EXTRA_USE_ROOT, mode.usesRoot)
            putExtra(ClipboardMonitorForegroundService.EXTRA_USE_LSPOSED, mode.usesLsposed)
            putExtra(
                ClipboardMonitorForegroundService.EXTRA_USE_HIDDEN_API,
                mode.usesHiddenApi,
            )
        }
        runCatching {
            ContextCompat.startForegroundService(appContext, intent)
        }.onFailure {
            Log.e(TAG, "start foreground service failed", it)
            _activeMode.value = null
        }
    }

    fun stop() {
        pendingMode = null
        mainHandler.removeCallbacks(dispatchStartRunnable)
        val intent = Intent(appContext, ClipboardMonitorForegroundService::class.java)
        appContext.stopService(intent)
        unbindListeningService()
        markListening(false)
        _activeMode.value = null
    }

    fun markListening(listening: Boolean) {
        _isListening.value = listening
    }

    fun dispatchPayload(payload: ClipboardPayload) {
        onPayloadCaptured?.invoke(payload)
    }

    fun unbindListeningService() {
        val connection = listeningServiceConnection ?: return
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            runCatching {
                listeningServiceArgs?.let { args ->
                    Shizuku.unbindUserService(args, connection, true)
                }
            }
        }
        listeningServiceConnection = null
    }

    fun hasShizukuPermission(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false) &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun requestShizukuPermission(requestCode: Int) {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return
        if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            return
        }
        Shizuku.requestPermission(requestCode)
    }

    fun isRootAvailable(): Boolean =
        if (PrivilegeGateway.isRootMode()) {
            TaskManagerUtil.peekPrivilegedAccess()
        } else {
            PrivilegeGateway.probeDirectRootAvailable()
        }

    companion object {
        private const val TAG = "ClipboardMonitor"

        fun peek(): ClipboardMonitorController? = instance

        @Volatile
        private var instance: ClipboardMonitorController? = null

        internal fun bindInstance(controller: ClipboardMonitorController) {
            instance = controller
        }
    }
}
