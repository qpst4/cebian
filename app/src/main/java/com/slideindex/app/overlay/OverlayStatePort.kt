package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.slideindex.app.notification.ActiveNotificationSnapshot
import com.slideindex.app.notification.ActiveNotificationSnapshotCodec
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.util.AppProcess
import com.slideindex.app.util.TriggerEnvironmentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 镜像保鲜期：overlay 25s 广播一次心跳，三倍余量足够，也避免误判"进程已死"。 */
    private const val STATE_STALE_MS = 90_000L
    const val HEARTBEAT_INTERVAL_HINT_MS = 25_000L
    private const val ACTION_STATE = "com.slideindex.app.action.OVERLAY_STATE"
    private const val ACTION_COMMAND = "com.slideindex.app.action.OVERLAY_COMMAND"
    private const val ACTION_ACTIVE_NOTIFICATIONS = "com.slideindex.app.action.ACTIVE_NOTIFICATIONS"

    private const val EXTRA_CONNECTED = "connected"
    private const val EXTRA_FOREGROUND = "foreground"
    private const val EXTRA_LOCKED = "locked"
    private const val EXTRA_COMMAND = "command"
    private const val EXTRA_ACTIVE_NOTIFICATIONS_JSON = "active_notifications_json"

    const val COMMAND_SYNC_SCREENSHOT_MONITORING = "sync_screenshot_monitoring"
    const val COMMAND_OTP_AUTOFILL = "otp_autofill"
    const val COMMAND_SYNC_CLIPBOARD_MONITORING = "sync_clipboard_monitoring"
    const val COMMAND_PUBLISH_ACTIVE_NOTIFICATIONS = "publish_active_notifications"
    const val COMMAND_RECOVER_ACCESSIBILITY = "recover_accessibility"
    /** 外部编辑页返回后，让 :overlay 恢复边角轮盘的可视层（旧实现是进程内静态回调，拆进程后失效）。 */
    const val COMMAND_RESUME_CORNER_OVERLAY = "resume_corner_overlay"

    private const val EXTRA_OTP_CODE = "otp_code"
    private const val EXTRA_OTP_RECORD_ID = "otp_record_id"

    // —— 主进程（及 :engine 等）持有的镜像 ——
    @Volatile
    private var mirrorConnected = false

    @Volatile
    private var mirrorForegroundPackage: String? = null

    @Volatile
    private var mirrorLockScreenActive = false

    /** 是否收到过 overlay 的状态广播：没收过时镜像的 false 只是「未知」，不能当成「掉线」。 */
    @Volatile
    private var mirrorStateReceived = false

    /** 最近一次收到 overlay 状态广播的时刻（本进程 elapsedRealtime）。看门狗用它判断 overlay 是否还活着。 */
    @Volatile
    private var mirrorStateAtMs = 0L

    /**
     * 通知栏「实时」快照镜像。
     *
     * 监听服务只在 `:overlay`，UI 在主进程：以前主进程直接读 `MediaNotificationListener.instance`
     * 永远是 null，「通知滤盒 → 实时」tab 一直显示「通知栏暂无通知」。
     * 现在 overlay 进程把快照广播出来，主进程存这份镜像给 UI 用。
     */
    private val _activeNotificationSnapshots =
        MutableStateFlow<List<ActiveNotificationSnapshot>?>(null)
    val activeNotificationSnapshots: StateFlow<List<ActiveNotificationSnapshot>?> =
        _activeNotificationSnapshots.asStateFlow()

    private var stateReceiver: BroadcastReceiver? = null
    private var commandReceiver: BroadcastReceiver? = null

    /** 非 overlay 进程读镜像；overlay 进程自己直接问监听实例（见实现方）。 */
    fun mirroredActiveNotificationSnapshots(): List<ActiveNotificationSnapshot>? =
        _activeNotificationSnapshots.value

    /** overlay 进程：把当前通知栏快照广播给其它进程。 */
    fun publishActiveNotifications(context: Context, snapshots: List<ActiveNotificationSnapshot>) {
        if (!AppProcess.isOverlay) return
        _activeNotificationSnapshots.value = snapshots
        val intent = Intent(ACTION_ACTIVE_NOTIFICATIONS).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_ACTIVE_NOTIFICATIONS_JSON, ActiveNotificationSnapshotCodec.encode(snapshots))
        }
        runCatching { context.applicationContext.sendBroadcast(intent) }
            .onFailure { Log.w(TAG, "publishActiveNotifications failed", it) }
    }

    /** 非 overlay 进程：请 overlay 立即重发一次快照（刚打开实时 tab / 刷新时用）。 */
    fun requestActiveNotificationsPublish(context: Context) {
        if (AppProcess.isOverlay) return
        sendCommand(context, COMMAND_PUBLISH_ACTIVE_NOTIFICATIONS)
    }

    /** 无障碍服务是否连接（主进程读镜像）。 */
    fun isServiceConnected(): Boolean =
        if (AppProcess.isOverlay) SlideIndexAccessibilityService.isConnected() else mirrorConnected

    /**
     * 镜像当前是否可信。
     *
     * overlay 进程永远可信（读本地静态）；其它进程只有在收到过一次状态广播之后才可信 ——
     * 否则 `mirrorConnected=false` 会被误当成「无障碍掉线」，
     * 导致主进程去抖断一个其实健康的绑定（真机现象：弹出「边缘手势未连接」但手势其实正常）。
     */
    fun hasServiceState(): Boolean = AppProcess.isOverlay || mirrorStateReceived

    /**
     * 镜像是否「新鲜」。
     *
     * overlay 进程每 [HEARTBEAT_INTERVAL_HINT_MS]（由 OverlayService 看门狗每 25s 广播一次）刷一次，
     * 超过 [maxAgeMs] 没刷新就认为 overlay 进程大概率已经不在了 —— 这是主进程能拿到的、
     * 判断「overlay 是否还活着」的唯一可靠信号。
     */
    fun isServiceStateFresh(
        nowMs: Long = android.os.SystemClock.elapsedRealtime(),
        maxAgeMs: Long = STATE_STALE_MS,
    ): Boolean = mirrorStateReceived && nowMs - mirrorStateAtMs <= maxAgeMs

    /** 距上次收到 overlay 状态广播过了多久；从没收到过返回 null。 */
    fun millisSinceServiceState(
        nowMs: Long = android.os.SystemClock.elapsedRealtime(),
    ): Long? = if (mirrorStateReceived) nowMs - mirrorStateAtMs else null

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
                when (intent?.action) {
                    ACTION_STATE -> {
                        mirrorConnected = intent.getBooleanExtra(EXTRA_CONNECTED, mirrorConnected)
                        mirrorLockScreenActive = intent.getBooleanExtra(EXTRA_LOCKED, mirrorLockScreenActive)
                        mirrorForegroundPackage = intent.getStringExtra(EXTRA_FOREGROUND)
                        mirrorStateReceived = true
                        mirrorStateAtMs = android.os.SystemClock.elapsedRealtime()
                    }

                    ACTION_ACTIVE_NOTIFICATIONS -> {
                        val json = intent.getStringExtra(EXTRA_ACTIVE_NOTIFICATIONS_JSON)
                        _activeNotificationSnapshots.value = ActiveNotificationSnapshotCodec.decode(json)
                    }
                }
            }
        }
        val filter = IntentFilter(ACTION_STATE).apply { addAction(ACTION_ACTIVE_NOTIFICATIONS) }
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

                    COMMAND_PUBLISH_ACTIVE_NOTIFICATIONS ->
                        runCatching {
                            com.slideindex.app.service.MediaNotificationListener
                                .publishActiveNotificationsSnapshot((ctx ?: context).applicationContext)
                        }.onFailure { Log.w(TAG, "publish active notifications failed", it) }

                    // 恢复/重绑只能在 overlay 进程做：无障碍实例与权威的连接状态都在这里。
                    COMMAND_RESUME_CORNER_OVERLAY ->
                        runCatching {
                            com.slideindex.app.overlay.corner.CornerGestureHost.resumeAfterSlotPicker()
                        }.onFailure { Log.w(TAG, "resume corner overlay failed", it) }

                    COMMAND_RECOVER_ACCESSIBILITY -> {
                        val appContext = (ctx ?: context).applicationContext
                        scope.launch {
                            val deps = runCatching {
                                dagger.hilt.android.EntryPointAccessors.fromApplication(
                                    appContext,
                                    com.slideindex.app.di.AppGraphEntryPoint::class.java,
                                ).dependencies()
                            }.getOrNull() ?: return@launch
                            runCatching {
                                com.slideindex.app.service.OverlayServiceLifecycle
                                    .recoverAccessibilityBinding(
                                        appContext,
                                        deps.settingsRepository.readSnapshot(),
                                    )
                            }.onFailure { Log.w(TAG, "recover accessibility failed", it) }
                            // 恢复完立刻回一帧状态，其它进程的镜像才不会一直停在「未知」。
                            publish(appContext, "recoverAccessibility")
                        }
                    }
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
