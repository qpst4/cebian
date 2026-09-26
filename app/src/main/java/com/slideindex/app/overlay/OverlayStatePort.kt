package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.util.AppProcess
import com.slideindex.app.util.TriggerEnvironmentState

/**
 * 跨进程的 overlay 状态端口。
 *
 * 多进程后 `:overlay` 与主进程各有自己的一份静态状态：
 * - 权威值只存在于 `:overlay`（无障碍实例、前台包名、锁屏态）；
 * - 主进程通过本端口读镜像，需要 overlay 做事时通过命令通道下发。
 *
 * 实现用同应用内广播（`setPackage` + `RECEIVER_NOT_EXPORTED`），
 * 状态变化频率低（切前台/锁屏/服务连接），足够且不引入 AIDL 编译期依赖。
 */
object OverlayStatePort {
    private const val TAG = "OverlayStatePort"
    private const val ACTION_STATE = "com.slideindex.app.action.OVERLAY_STATE"
    private const val ACTION_COMMAND = "com.slideindex.app.action.OVERLAY_COMMAND"

    private const val EXTRA_CONNECTED = "connected"
    private const val EXTRA_FOREGROUND = "foreground"
    private const val EXTRA_LOCKED = "locked"
    private const val EXTRA_COMMAND = "command"

    const val COMMAND_SYNC_SCREENSHOT_MONITORING = "sync_screenshot_monitoring"
    const val COMMAND_OTP_AUTOFILL = "otp_autofill"
    const val COMMAND_SYNC_CLIPBOARD_MONITORING = "sync_clipboard_monitoring"

    private const val EXTRA_OTP_CODE = "otp_code"
    private const val EXTRA_OTP_RECORD_ID = "otp_record_id"

    // —— 主进程（及 :engine 等）持有的镜像 ——
    @Volatile
    private var mirrorConnected = false

    @Volatile
    private var mirrorForegroundPackage: String? = null

    @Volatile
    private var mirrorLockScreenActive = false

    private var stateReceiver: BroadcastReceiver? = null
    private var commandReceiver: BroadcastReceiver? = null

    /** 无障碍服务是否连接（主进程读镜像）。 */
    fun isServiceConnected(): Boolean =
        if (AppProcess.isOverlay) SlideIndexAccessibilityService.isConnected() else mirrorConnected

    /** 当前前台包名（主进程读镜像）。 */
    fun foregroundPackage(): String? =
        if (AppProcess.isOverlay) {
            OverlayService.foregroundPackage ?: SlideIndexAccessibilityService.currentForegroundPackage()
        } else {
            mirrorForegroundPackage
        }

    fun isLockScreenActive(): Boolean =
        if (AppProcess.isOverlay) TriggerEnvironmentState.lockScreenActive else mirrorLockScreenActive

    /** 只有 overlay 进程调用：把权威状态广播给其它进程。 */
    fun publish(context: Context, reason: String) {
        if (!AppProcess.isOverlay) return
        val intent = Intent(ACTION_STATE).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_CONNECTED, SlideIndexAccessibilityService.isConnected())
            putExtra(
                EXTRA_FOREGROUND,
                OverlayService.foregroundPackage ?: SlideIndexAccessibilityService.currentForegroundPackage(),
            )
            putExtra(EXTRA_LOCKED, TriggerEnvironmentState.lockScreenActive)
        }
        runCatching { context.applicationContext.sendBroadcast(intent) }
            .onFailure { Log.w(TAG, "publish($reason) failed", it) }
    }

    /** 非 overlay 进程启动时调用，开始维护镜像。 */
    fun startMirroring(context: Context) {
        if (AppProcess.isOverlay || stateReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ACTION_STATE) return
                mirrorConnected = intent.getBooleanExtra(EXTRA_CONNECTED, mirrorConnected)
                mirrorLockScreenActive = intent.getBooleanExtra(EXTRA_LOCKED, mirrorLockScreenActive)
                mirrorForegroundPackage = intent.getStringExtra(EXTRA_FOREGROUND)
            }
        }
        val filter = IntentFilter(ACTION_STATE)
        runCatching {
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            stateReceiver = receiver
        }.onFailure { Log.w(TAG, "startMirroring failed", it) }
    }

    /** 非 overlay 进程向 overlay 下发命令。 */
    fun sendCommand(context: Context, command: String) {
        val intent = Intent(ACTION_COMMAND).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_COMMAND, command)
        }
        runCatching { context.applicationContext.sendBroadcast(intent) }
            .onFailure { Log.w(TAG, "sendCommand($command) failed", it) }
    }

    /**
     * 验证码注入只能在 overlay 进程做（无障碍实例在那里）。
     * 短信接收器在默认进程，因此把注入请求转过来。
     */
    fun sendOtpAutoFill(context: Context, code: String, recordId: String?) {
        val intent = Intent(ACTION_COMMAND).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_COMMAND, COMMAND_OTP_AUTOFILL)
            putExtra(EXTRA_OTP_CODE, code)
            putExtra(EXTRA_OTP_RECORD_ID, recordId)
        }
        runCatching { context.applicationContext.sendBroadcast(intent) }
            .onFailure { Log.w(TAG, "sendOtpAutoFill failed", it) }
    }

    /** overlay 进程启动时调用，接收来自主进程的命令。 */
    fun startCommandReceiver(context: Context) {
        if (!AppProcess.isOverlay || commandReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ACTION_COMMAND) return
                when (intent.getStringExtra(EXTRA_COMMAND)) {
                    COMMAND_SYNC_SCREENSHOT_MONITORING ->
                        runCatching { SlideIndexAccessibilityService.accessibilityInstance()?.syncScreenshotMonitoring() }

                    COMMAND_OTP_AUTOFILL -> {
                        val code = intent.getStringExtra(EXTRA_OTP_CODE) ?: return
                        val recordId = intent.getStringExtra(EXTRA_OTP_RECORD_ID)
                        val appContext = (ctx ?: context).applicationContext
                        val deps = runCatching {
                            dagger.hilt.android.EntryPointAccessors.fromApplication(
                                appContext,
                                com.slideindex.app.di.AppGraphEntryPoint::class.java,
                            ).dependencies()
                        }.getOrNull() ?: return
                        runCatching {
                            com.slideindex.app.otp.OtpAutoInputOrchestrator.requestAutoFill(
                                context = appContext,
                                code = code,
                                settings = deps.settingsRepository.readSnapshot(),
                                recordId = recordId,
                            )
                        }.onFailure { Log.w(TAG, "otp autofill failed", it) }
                    }

                    COMMAND_SYNC_CLIPBOARD_MONITORING ->
                        runCatching {
                            com.slideindex.app.clipboard.ClipboardAccess.repository
                                ?.syncClipboardMonitoringFromSettings()
                        }.onFailure { Log.w(TAG, "sync clipboard monitoring failed", it) }
                }
            }
        }
        val filter = IntentFilter(ACTION_COMMAND)
        runCatching {
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            commandReceiver = receiver
        }.onFailure { Log.w(TAG, "startCommandReceiver failed", it) }
    }
}
