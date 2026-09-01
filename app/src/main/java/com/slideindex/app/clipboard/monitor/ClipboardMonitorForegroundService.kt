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
import com.slideindex.app.util.PermissionHelper
import java.io.File
import java.lang.ref.WeakReference
import rikka.shizuku.Shizuku

class ClipboardMonitorForegroundService : Service() {
    private val tag = "ClipboardMonitorFg"
    private val mainHandler = ChangeHandler(this)
    private var useRoot = false
    private var useHiddenApi = false
    private var listenerThread: Thread? = null
    private var lastChangedTime = 0L
    private val changedMinIntervalMs = 200L
    private var listenerService: IClipboardListenerService? = null
    private var bindGeneration = 0

    private val clipboardListenerCallback by lazy {
        object : IOnClipboardChanged.Stub() {
            override fun onChanged(logLine: String?) {
                val controller = ClipboardMonitorController.peek() ?: return
                if (!shouldTriggerClipboardRead(logLine, controller.config.applicationId)) {
                    return
                }
                if (controller.config.ignoreNextCopy) {
                    controller.config.ignoreNextCopy = false
                } else {
                    mainHandler.sendEmptyMessage(0)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        promoteToForeground(
            getString(R.string.clipboard_monitor_notification_waiting_title),
            getString(R.string.clipboard_monitor_notification_waiting_text),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        useRoot = intent?.getBooleanExtra(EXTRA_USE_ROOT, false) == true
        useHiddenApi = intent?.getBooleanExtra(EXTRA_USE_HIDDEN_API, false) == true
        promoteToForeground(
            ClipboardMonitorNotificationTexts.waitingTitle(this),
            ClipboardMonitorNotificationTexts.waitingText(this, useRoot, useHiddenApi),
        )

        mainHandler.removeCallbacksAndMessages(null)
        bindGeneration++
        val currentBindGeneration = bindGeneration

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
        listenerThread = Thread({
            try {
                val path = File(
                    applicationContext.getExternalFilesDir(null),
                    LISTENER_ZIP_ASSET,
                ).path
                service.startListening(clipboardListenerCallback, useRoot, path, useHiddenApi)
            } catch (e: Exception) {
                Log.w(tag, "privileged listening failed", e)
                updateNotification(
                    getString(R.string.clipboard_monitor_notification_error_title),
                    e.message ?: getString(R.string.clipboard_monitor_notification_error_unknown),
                )
            } finally {
                ClipboardMonitorController.peek()?.markListening(false)
                updateNotification(
                    getString(R.string.clipboard_monitor_notification_stopped_title),
                    getString(R.string.clipboard_monitor_notification_stopped_text),
                )
            }
        }, "ClipboardPrivilegedListener").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun showFloatFocusView() {
        if (!hasOverlayCapability()) {
            Log.w(tag, "overlay capability missing")
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastChangedTime < changedMinIntervalMs) return
        lastChangedTime = now
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
        mainHandler.removeCallbacksAndMessages(null)
        ClipboardMonitorController.peek()?.markListening(false)
        listenerThread?.interrupt()
        listenerThread = null
        if (useRoot || isShizukuBinderAvailable()) {
            runCatching { listenerService?.exit() }
        }
        ClipboardMonitorController.peek()?.unbindListeningService()
        if (!useRoot) {
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
            outer.get()?.showFloatFocusView()
        }
    }

    companion object {
        const val EXTRA_USE_ROOT = "useRoot"
        const val EXTRA_USE_HIDDEN_API = "useHiddenApi"
        private const val CHANNEL_ID = "clipboard_monitor"
        private const val NOTIFICATION_ID = 4102
        private const val CONTROLLER_RETRY_MS = 400L
        private const val BIND_DELAY_MS = 500L

        internal fun shouldTriggerClipboardRead(logLine: String?, applicationId: String): Boolean {
            if (logLine == null) return true
            if (applicationId.isNotEmpty() && logLine.contains(applicationId)) return false
            return true
        }
    }
}
