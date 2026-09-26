package com.slideindex.app.clipboard.monitor

/**
 * Based on [ClipboardListener](https://github.com/aa2013/ClipboardListener) (MIT).
 * Adapted for Cebian clipboard history / stash integration.
 */
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.slideindex.app.MainActivity
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardFocusReader
import com.slideindex.app.clipboard.ClipboardReader
import com.slideindex.app.settings.ClipboardMonitoringMode
import com.slideindex.app.util.PermissionHelper
import java.io.File
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class ClipboardMonitorForegroundService : Service() {
    private val tag = "ClipboardMonitorFg"
    private val mainHandler = ChangeHandler(this)
    private var useRoot = false
    private var useHiddenApi = false
    private var useStandard = false
    private var useLsposed = false
    private var listenerThread: Thread? = null
    private var lastChangedTime = 0L
    private val changedMinIntervalMs = 200L
    private var listenerService: IClipboardListenerService? = null
    private var bindGeneration = 0
    private var standardClipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    /** 周期把监听状态广播出去，让主进程（设置页）的镜像收敛到真实状态。 */
    private val statusHeartbeatRunnable = object : Runnable {
        override fun run() {
            ClipboardMonitorController.peek()?.republishStatus()
            mainHandler.postDelayed(this, STATUS_HEARTBEAT_MS)
        }
    }

    /** 通知状态跟随用（见 [startNotificationStateCollector]）。 */
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 按设置同步监听时用：放到后台线程，Root 可用性探测才不会退化成"未知=false"。 */
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var notificationCollectorStarted = false

    private val clipboardListenerCallback by lazy {
        object : IOnClipboardChanged.Stub() {
            override fun onChanged(logLine: String?) {
                val controller = ClipboardMonitorController.peek() ?: return
                if (!shouldTriggerClipboardRead(logLine, controller.config.applicationId)) {
                    return
                }
                if (controller.config.shouldIgnoreEvent()) return
                mainHandler.sendEmptyMessage(MSG_CLIPBOARD_CHANGED)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 状态心跳：设置页在主进程，只能靠广播镜像；周期重发保证镜像一定收敛（丢帧/时序问题自愈）。
        mainHandler.post(statusHeartbeatRunnable)
        mainHandler.postDelayed(statusHeartbeatRunnable, STATUS_HEARTBEAT_MS)
        // 先把进程标记成前台：冷启动 / 装机替换 / 开机重活期间，5 秒窗口很容易被挤掉
        // （历史 ForegroundServiceDidNotStartInTimeException 的成因）。
        // 渠道与正式文案随后补齐，这里用最小通知占位，失败也只记日志。
        runCatching {
            val bootstrap = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("")
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, bootstrap, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, bootstrap)
            }
        }.onFailure { Log.w(tag, "bootstrap startForeground failed", it) }
        ensureChannel()
        promoteToForeground(
            getString(R.string.clipboard_monitor_notification_waiting_title),
            getString(R.string.clipboard_monitor_notification_waiting_text),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 由其它进程按"跟随设置"拉起时，不带模式参数：在这里（监听进程内）自己解析模式。
        // 好处是 Shizuku / Root 可用性、LSPosed 探测都只在本进程判定一次，调用方不用猜。
        if (intent?.getBooleanExtra(EXTRA_FROM_SETTINGS, false) == true) {
            promoteToForeground(
                ClipboardMonitorNotificationTexts.waitingTitle(this),
                ClipboardMonitorNotificationTexts.waitingText(this, false, false, false, false),
            )
            syncFromSettingsWithRetries(attempt = 0)
            return START_NOT_STICKY
        }
        useStandard = intent?.getBooleanExtra(EXTRA_USE_STANDARD, false) == true
        useLsposed = intent?.getBooleanExtra(EXTRA_USE_LSPOSED, false) == true
        useRoot = intent?.getBooleanExtra(EXTRA_USE_ROOT, false) == true
        useHiddenApi = intent?.getBooleanExtra(EXTRA_USE_HIDDEN_API, false) == true
        promoteToForeground(
            ClipboardMonitorNotificationTexts.waitingTitle(this),
            ClipboardMonitorNotificationTexts.waitingText(
                this,
                useRoot,
                useHiddenApi,
                useStandard,
                useLsposed,
            ),
        )

        mainHandler.removeCallbacksAndMessages(null)
        bindGeneration++
        val currentBindGeneration = bindGeneration

        stopStandardListening()
        runCatching { listenerService?.stopListening() }
        listenerService = null
        listenerThread?.interrupt()
        listenerThread = null
        val controller = ClipboardMonitorController.peek()
        if (controller == null) {
            Log.w(tag, "controller not initialized, retrying")
            mainHandler.postDelayed({
                if (ClipboardMonitorController.peek() == null) {
                    Log.e(tag, "controller still unavailable after retry")
                    updateNotification(
                        getString(R.string.clipboard_monitor_notification_error_title),
                        getString(R.string.clipboard_monitor_notification_error_unknown),
                    )
                    stopSelf()
                } else {
                    onStartCommand(intent, flags, startId)
                }
            }, CONTROLLER_RETRY_MS)
            return START_NOT_STICKY
        }
        // 公开监听在所有模式下常驻：作为兜底事件源，特权通道哑掉/漏拍时仍能收到变更。
        startPrimaryClipChangedListener(controller)
        startNotificationStateCollector(controller)

        // LSPosed 模式走和标准模式同一条读取路径：白名单生效时系统直接放行，不需要焦点探针。
        if (useStandard || useLsposed) {
            controller.markListening(true)
            updateNotification(
                ClipboardMonitorNotificationTexts.runningTitle(this),
                ClipboardMonitorNotificationTexts.runningText(
                    this,
                    useRoot,
                    useHiddenApi,
                    useStandard,
                    useLsposed,
                ),
            )
            return START_NOT_STICKY
        }
        if (!useRoot) {
            Shizuku.addBinderReceivedListenerSticky(onBinderReceivedListener)
            Shizuku.addBinderDeadListener(onBinderDeadListener)
        }
        if (useHiddenApi && !copyAssetToExternalPrivateDir(applicationContext, LISTENER_ZIP_ASSET)) {
            updateNotification(
                getString(R.string.clipboard_monitor_notification_error_title),
                getString(R.string.clipboard_monitor_notification_listener_asset_error),
            )
            stopSelf()
            return START_NOT_STICKY
        }
        if (useRoot) {
            if (controller.isRootAvailable().not()) {
                updateNotification(
                    getString(R.string.clipboard_monitor_notification_error_title),
                    getString(R.string.clipboard_monitor_notification_root_unavailable),
                )
                controller.markListening(false)
                stopSelf()
                return START_NOT_STICKY
            }
            listenerService = ClipboardListenerService()
            controller.markListening(true)
            updateNotification(
                ClipboardMonitorNotificationTexts.runningTitle(this),
                ClipboardMonitorNotificationTexts.runningText(this, useRoot, useHiddenApi),
            )
            startPrivilegedListening()
        } else {
            controller.unbindListeningService()
            bindShizukuListener(controller, currentBindGeneration)
        }
        return START_NOT_STICKY
    }

    /**
     * 按设置同步监听，失败自动重试几次。
     *
     * 两个"必须点第二次才生效"的场景都在这里兜住：
     * - **切到 Root 通道**：`probeDirectRootAvailable()` 在主线程且缓存为空时一律返回 false
     *   （未知当成不可用），异步探测结果要等下一次调用才用得上 —— 于是第一次点 Root 被判定
     *   "root unavailable" 直接 stop，第二次点才起来。放 IO 线程做才是真探测，并把结果写进缓存。
     * - **Shizuku 刚起 / 刚切通道**：binder 与用户服务都需要一点时间，一次不成再来两次。
     */
    private fun syncFromSettingsWithRetries(attempt: Int) {
        workerScope.launch {
            val repository = com.slideindex.app.clipboard.ClipboardAccess.repository
            if (repository == null) {
                Log.w(tag, "clipboard repository unavailable, stop monitor")
                stopSelf()
                return@launch
            }
            // 交给仓库走标准路径：解析模式 → 再用带模式参数的 intent 重新启动本服务。
            // 这里**不要**再叠一层"没在监听就重试"：控制器内部（runOnMainWhenCalm + startIfNeeded）
            // 已经会等主线程空闲并处理时序，外层再重试会起出重复的 Shizuku 用户服务实例，
            // 反而把切换通道拖慢（真机抓到同一个切换里出现两个 UserService record）。
            withContext(Dispatchers.IO) { repository.syncClipboardMonitoringFromSettings() }
        }
    }

    private fun bindShizukuListener(
        controller: ClipboardMonitorController,
        generation: Int,
    ) {
        val args = controller.listeningServiceArgs ?: return
        val connection = object : ServiceConnection {
            private var bound: IClipboardListenerService? = null

            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (generation != bindGeneration) {
                    runCatching { IClipboardListenerService.Stub.asInterface(binder)?.stopListening() }
                    return
                }
                if (bound != null) {
                    runCatching { bound?.stopListening() }
                    return
                }
                val service = IClipboardListenerService.Stub.asInterface(binder)
                bound = service
                listenerService = service
                controller.listeningServiceConnection = this
                controller.markListening(true)
                updateNotification(
                    ClipboardMonitorNotificationTexts.runningTitle(this@ClipboardMonitorForegroundService),
                    ClipboardMonitorNotificationTexts.runningText(
                        this@ClipboardMonitorForegroundService,
                        useRoot,
                        useHiddenApi,
                    ),
                )
                startPrivilegedListening()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (generation != bindGeneration) return
                stopShizukuListeningLocally("listener service disconnected")
            }
        }
        controller.listeningServiceConnection = connection
        mainHandler.postDelayed({
            if (generation != bindGeneration) return@postDelayed
            if (!isShizukuBinderAvailable()) {
                stopShizukuListeningLocally("Shizuku unavailable")
                return@postDelayed
            }
            runCatching {
                Shizuku.bindUserService(args, connection)
            }.onFailure {
                stopShizukuListeningLocally("bind failed: ${it.message}")
            }
        }, BIND_DELAY_MS)
    }

    private fun startPrivilegedListening() {
        val service = listenerService ?: return
        listenerThread?.interrupt()
        // 有了新的一代启动就别再被上一代的收尾改状态：重启时旧线程会从阻塞的 Binder 调用里返回，
        // 它的 finally 若不判代次，会把刚建立的新状态又翻回"已停止"。
        val generation = bindGeneration
        listenerThread = Thread({
            var attempt = 0
            while (true) {
                try {
                    val path = File(
                        applicationContext.getExternalFilesDir(null),
                        LISTENER_ZIP_ASSET,
                    ).path
                    service.startListening(clipboardListenerCallback, useRoot, path, useHiddenApi)
                } catch (e: Exception) {
                    Log.w(tag, "privileged listening failed (attempt=$attempt)", e)
                }
                // 上一代已作废（重启/停止）就别再管状态。
                if (generation != bindGeneration) return@Thread
                // 通道刚起就断（用户服务重启、logcat 被杀、隐藏接口报错）时自动重连，
                // 否则状态会"运行中→未在监听"来回跳，用户看到的就是"没在监听"。
                if (attempt >= MAX_LISTEN_RETRIES) break
                attempt++
                Log.w(tag, "restarting privileged listening (attempt=$attempt)")
                try {
                    Thread.sleep(LISTEN_RETRY_DELAY_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                if (generation != bindGeneration) return@Thread
            }
            ClipboardMonitorController.peek()?.markListening(false)
            updateNotification(
                getString(R.string.clipboard_monitor_notification_stopped_title),
                getString(R.string.clipboard_monitor_notification_stopped_text),
            )
        }, "ClipboardPrivilegedListener").also {
            it.isDaemon = true
            it.start()
        }
    }

    /**
     * 注册公开剪贴板监听（`addPrimaryClipChangedListener`）。
     *
     * 与特权通道无关：任何模式下都挂着，作为"变更事件"的兜底来源。
     * 系统在后台也会派发该回调；能不能读到内容由白名单 / 焦点决定，与这里无关。
     */
    /**
     * 让常驻通知跟着真实监听状态走。
     *
     * 任何路径（Shizuku 绑定成功 / Root 就绪 / 标准或 LSPosed 启动）把状态置为「在监听」后，
     * 这里都会补刷一次「运行中」文案——此前出现过通知停在「正在启动」的情况。
     */
    private fun startNotificationStateCollector(controller: ClipboardMonitorController) {
        if (notificationCollectorStarted) return
        notificationCollectorStarted = true
        notificationScope.launch {
            controller.isListeningFlow.collect { listening ->
                if (!listening) return@collect
                updateNotification(
                    ClipboardMonitorNotificationTexts.runningTitle(this@ClipboardMonitorForegroundService),
                    ClipboardMonitorNotificationTexts.runningText(
                        this@ClipboardMonitorForegroundService,
                        useRoot,
                        useHiddenApi,
                        useStandard,
                        useLsposed,
                    ),
                )
            }
        }
    }

    private fun startPrimaryClipChangedListener(controller: ClipboardMonitorController) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            mainHandler.post {
                if (controller.config.shouldIgnoreEvent()) return@post
                mainHandler.sendEmptyMessage(MSG_CLIPBOARD_CHANGED)
            }
        }
        standardClipListener = listener
        clipboard.addPrimaryClipChangedListener(listener)
    }

    private fun stopStandardListening() {
        val listener = standardClipListener ?: return
        (getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.removePrimaryClipChangedListener(listener)
        standardClipListener = null
    }

    private fun handleClipboardChanged() {
        val now = System.currentTimeMillis()
        if (now - lastChangedTime < changedMinIntervalMs) return
        lastChangedTime = now
        if (useStandard || useLsposed) {
            ClipboardReader.read(applicationContext)?.let { payload ->
                ClipboardMonitorController.peek()?.dispatchPayload(payload)
            }
            return
        }
        if (!hasOverlayCapability()) {
            Log.w(tag, "overlay capability missing")
            return
        }
        ClipboardFocusReader.read(applicationContext) { payload ->
            if (payload != null) {
                ClipboardMonitorController.peek()?.dispatchPayload(payload)
            } else {
                Log.d(tag, "clipboard read returned null")
            }
        }
    }

    private fun hasOverlayCapability(): Boolean =
        PermissionHelper.canDrawOverlays(this)

    private fun stopShizukuListeningLocally(reason: String) {
        Log.w(tag, "stop locally: $reason")
        listenerThread?.interrupt()
        listenerThread = null
        listenerService = null
        ClipboardMonitorController.peek()?.markListening(false)
        ClipboardMonitorController.peek()?.unbindListeningService()
        updateNotification(
            getString(R.string.clipboard_monitor_notification_shizuku_disconnected_title),
            getString(R.string.clipboard_monitor_notification_shizuku_disconnected_text),
        )
    }

    override fun onDestroy() {
        bindGeneration++
        notificationScope.cancel()
        workerScope.cancel()
        mainHandler.removeCallbacks(statusHeartbeatRunnable)
        mainHandler.removeCallbacksAndMessages(null)
        stopStandardListening()
        ClipboardMonitorController.peek()?.markListening(false)
        listenerThread?.interrupt()
        listenerThread = null
        if (useRoot || isShizukuBinderAvailable()) {
            runCatching { listenerService?.exit() }
        }
        ClipboardMonitorController.peek()?.unbindListeningService()
        if (!useRoot && !useStandard && !useLsposed) {
            Shizuku.removeBinderReceivedListener(onBinderReceivedListener)
            Shizuku.removeBinderDeadListener(onBinderDeadListener)
        }
        listenerService = null
        stopForegroundCompat()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground(title: String, text: String) {
        val notification = runCatching {
            buildNotification(title, text)
        }.getOrElse { error ->
            Log.e(tag, "build notification failed", error)
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .build()
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { error ->
            Log.e(tag, "startForeground failed", error)
            startForeground(
                NOTIFICATION_ID,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setContentTitle(title)
                    .build(),
            )
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.clipboard_monitor_notification_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.clipboard_monitor_notification_channel_desc)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun isShizukuBinderAvailable(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    private val onBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(tag, "Shizuku binder received")
    }

    private val onBinderDeadListener = Shizuku.OnBinderDeadListener {
        stopShizukuListeningLocally("binder dead")
    }

    private class ChangeHandler(service: ClipboardMonitorForegroundService) : Handler(Looper.getMainLooper()) {
        private val outer = WeakReference(service)

        override fun handleMessage(msg: Message) {
            outer.get()?.handleClipboardChanged()
        }
    }

    companion object {
        const val EXTRA_USE_ROOT = "useRoot"
        const val EXTRA_USE_HIDDEN_API = "useHiddenApi"
        const val EXTRA_USE_STANDARD = "useStandard"
        const val EXTRA_USE_LSPOSED = "useLsposed"

        /** 由其它进程发起、模式留给自己解析（见 onStartCommand 开头）。 */
        const val EXTRA_FROM_SETTINGS = "fromSettings"

        /** 带模式参数的启动 intent（进程内 / 跨进程都用这一个构造点）。 */
        fun modeIntent(
            context: android.content.Context,
            mode: ClipboardMonitoringMode,
        ): Intent = Intent(context, ClipboardMonitorForegroundService::class.java).apply {
            putExtra(EXTRA_USE_STANDARD, mode.usesStandardApi)
            putExtra(EXTRA_USE_ROOT, mode.usesRoot)
            putExtra(EXTRA_USE_LSPOSED, mode.usesLsposed)
            putExtra(EXTRA_USE_HIDDEN_API, mode.usesHiddenApi)
        }

        private const val MSG_CLIPBOARD_CHANGED = 1
        private const val CHANNEL_ID = "clipboard_monitor"
        private const val NOTIFICATION_ID = 4102
        private const val CONTROLLER_RETRY_MS = 400L
        private const val BIND_DELAY_MS = 500L
        private const val STATUS_HEARTBEAT_MS = 20_000L
        private const val MAX_LISTEN_RETRIES = 5
        private const val LISTEN_RETRY_DELAY_MS = 1_500L
        private const val MAX_FROM_SETTINGS_RETRIES = 2
        private const val FROM_SETTINGS_RETRY_MS = 1_500L

        internal fun shouldTriggerClipboardRead(logLine: String?, applicationId: String): Boolean {
            if (logLine == null) return true
            if (applicationId.isNotEmpty() && logLine.contains(applicationId)) return false
            return true
        }
    }
}
