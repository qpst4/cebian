package com.slideindex.app.clipboard.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.slideindex.app.settings.ClipboardMonitoringMode
import com.slideindex.app.util.AppProcess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 剪贴板监听的真实运行状态。 */
data class ClipboardMonitorStatus(
    val listening: Boolean,
    val mode: ClipboardMonitoringMode?,
)

/**
 * 剪贴板监听状态的跨进程镜像。
 *
 * 监听服务跑在独立进程 `:clipboard`，而设置页在主进程：
 * 主进程里 `ClipboardMonitorController.isListeningFlow` 永远是 false（那是**本进程**的单例），
 * 于是设置页会出现"明明在监听却显示未在监听"的假状态。
 *
 * 这里由监听进程广播真实状态，其它进程维护镜像给 UI 用；
 * 打开设置页时可以 [requestStatus] 让监听进程立刻重发一次，避免首帧是空的。
 */
object ClipboardMonitorStatusPort {
    private const val TAG = "ClipboardMonitorStatus"
    private const val ACTION_STATUS = "com.slideindex.app.action.CLIPBOARD_MONITOR_STATUS"
    private const val ACTION_STATUS_REQUEST = "com.slideindex.app.action.CLIPBOARD_MONITOR_STATUS_REQUEST"
    private const val EXTRA_LISTENING = "listening"
    private const val EXTRA_MODE = "mode"

    private val _status = MutableStateFlow<ClipboardMonitorStatus?>(null)
    val status: StateFlow<ClipboardMonitorStatus?> = _status.asStateFlow()

    private var statusReceiver: BroadcastReceiver? = null
    private var requestReceiver: BroadcastReceiver? = null

    /** 只有监听进程可以发布权威状态。 */
    fun publish(context: Context, listening: Boolean, mode: ClipboardMonitoringMode?) {
        if (!AppProcess.isClipboardMonitor) return
        val previous = _status.value
        _status.value = ClipboardMonitorStatus(listening, mode)
        if (previous == null || previous.listening != listening || previous.mode != mode) {
            Log.w(TAG, "publish listening=$listening mode=$mode")
        }
        val intent = Intent(ACTION_STATUS).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_LISTENING, listening)
            putExtra(EXTRA_MODE, mode?.name)
        }
        runCatching { context.applicationContext.sendBroadcast(intent) }
            .onFailure { Log.w(TAG, "publish failed", it) }
    }

    /** 每个进程启动时调用：监听进程收"请重发"，其它进程收状态镜像。 */
    fun start(context: Context) {
        if (AppProcess.isClipboardMonitor) {
            startRequestReceiver(context)
        } else {
            startStatusReceiver(context)
        }
    }

    /** 非监听进程请求重发一次状态（打开设置页 / 切到前台时用）。 */
    fun requestStatus(context: Context) {
        if (AppProcess.isClipboardMonitor) return
        val intent = Intent(ACTION_STATUS_REQUEST).apply { setPackage(context.packageName) }
        runCatching { context.applicationContext.sendBroadcast(intent) }
            .onFailure { Log.w(TAG, "requestStatus failed", it) }
    }

    private fun startStatusReceiver(context: Context) {
        if (statusReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ACTION_STATUS) return
                val mode = intent.getStringExtra(EXTRA_MODE)
                    ?.let { raw -> runCatching { ClipboardMonitoringMode.valueOf(raw) }.getOrNull() }
                val next = ClipboardMonitorStatus(
                    listening = intent.getBooleanExtra(EXTRA_LISTENING, false),
                    mode = mode,
                )
                if (_status.value != next) {
                    Log.w(TAG, "mirror listening=${next.listening} mode=${next.mode}")
                }
                _status.value = next
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                IntentFilter(ACTION_STATUS),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            statusReceiver = receiver
        }.onFailure { Log.w(TAG, "startStatusReceiver failed", it) }
    }

    private fun startRequestReceiver(context: Context) {
        if (requestReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ACTION_STATUS_REQUEST) return
                ClipboardMonitorController.peek()?.republishStatus()
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                IntentFilter(ACTION_STATUS_REQUEST),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            requestReceiver = receiver
        }.onFailure { Log.w(TAG, "startRequestReceiver failed", it) }
    }
}
