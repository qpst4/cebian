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
import rikka.shizuku.Shizuku

@Singleton
class ClipboardMonitorController @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    val config = ClipboardMonitorConfig().apply {
        applicationId = appContext.packageName
    }

    @Volatile
    var isListening: Boolean = false
        private set

    @Volatile
    private var activeMode: ClipboardMonitoringMode? = null

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
        activeMode = mode
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
        if (isListening && activeMode == mode) return true
        return restart(mode)
    }

    fun restart(mode: ClipboardMonitoringMode): Boolean {
        if (!ClipboardMonitorProcess.isMainProcess(appContext)) {
            Log.w(TAG, "skip clipboard monitor in subprocess")
            return false
        }
        if (!canStart(mode)) {
            Log.w(TAG, "backend unavailable for mode=$mode, stopping listener")
            stop()
            return false
        }
        pendingMode = mode
        ClipboardMonitorStartup.runOnMainWhenReady {
            mainHandler.removeCallbacks(dispatchStartRunnable)
            mainHandler.post(dispatchStartRunnable)
        }
        return true
    }

    fun start(mode: ClipboardMonitoringMode): Boolean {
        if (!ClipboardMonitorProcess.isMainProcess(appContext)) {
            Log.w(TAG, "skip clipboard monitor in subprocess")
            return false
        }
        if (isListening && activeMode == mode) return true
        if (!canStart(mode)) return false
        pendingMode = mode
        ClipboardMonitorStartup.runOnMainWhenIdle {
            mainHandler.removeCallbacks(dispatchStartRunnable)
            mainHandler.post(dispatchStartRunnable)
        }
        return true
    }

    private fun canStart(mode: ClipboardMonitoringMode): Boolean {
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

    private fun startForegroundServiceInternal(mode: ClipboardMonitoringMode) {
        if (!canStart(mode)) return
        val intent = Intent(appContext, ClipboardMonitorForegroundService::class.java).apply {
            putExtra(ClipboardMonitorForegroundService.EXTRA_USE_ROOT, mode.usesRoot)
            putExtra(
                ClipboardMonitorForegroundService.EXTRA_USE_HIDDEN_API,
                mode.usesHiddenApi,
            )
        }
        runCatching {
            ContextCompat.startForegroundService(appContext, intent)
        }.onFailure {
            Log.e(TAG, "start foreground service failed", it)
            activeMode = null
        }
    }

    fun stop() {
        pendingMode = null
        mainHandler.removeCallbacks(dispatchStartRunnable)
        val intent = Intent(appContext, ClipboardMonitorForegroundService::class.java)
        appContext.stopService(intent)
        unbindListeningService()
        markListening(false)
        activeMode = null
    }

    fun markListening(listening: Boolean) {
        isListening = listening
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
