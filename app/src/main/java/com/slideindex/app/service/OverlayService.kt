package com.slideindex.app.service



import android.app.Notification

import android.app.NotificationChannel

import android.app.NotificationManager

import android.app.PendingIntent

import android.content.Intent

import android.os.IBinder

import androidx.core.app.NotificationCompat

import androidx.lifecycle.LifecycleService

import com.slideindex.app.MainActivity

import com.slideindex.app.R

import com.slideindex.app.SlideIndexApp

import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.OverlayManager
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.shell.ShellCommand

import com.slideindex.app.util.ForegroundAppTracker

import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.TaskManagerUtil

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.SupervisorJob

import kotlinx.coroutines.cancel

import kotlinx.coroutines.flow.collectLatest

import kotlinx.coroutines.launch



class OverlayService : LifecycleService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var overlayManager: OverlayManager? = null
    private var foregroundTracker: ForegroundAppTracker? = null
    private var previewActive = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY



    override fun onCreate() {

        super.onCreate()

        createNotificationChannel()

        startForeground(NOTIFICATION_ID, buildNotification())



        val app = application as SlideIndexApp

        overlayManager = OverlayManager(

            context = this,

            appRepository = app.appRepository,

            scope = serviceScope,

            onShellCommandsPersist = { commands ->
                serviceScope.launch {
                    app.settingsRepository.setShellCommands(commands)
                }
            },

            onQuickLauncherItemsPersist = { items ->
                serviceScope.launch {
                    app.settingsRepository.setQuickLauncherItems(items)
                }
            },

        )

        foregroundTracker = ForegroundAppTracker(this, serviceScope)

        serviceScope.launch(Dispatchers.Default) {
            app.appRepository.loadApps()
        }

        if (TaskManagerUtil.hasPermission()) {
            TaskManagerUtil.warmUp()
        }

        serviceScope.launch {

            foregroundTracker!!.foregroundPackage.collectLatest { packageName ->

                OverlayService.foregroundPackage = packageName
                overlayManager?.updateForegroundPackage(packageName)

            }

        }



        serviceScope.launch {

            app.settingsRepository.settings.collectLatest { settings ->

                if (!PermissionHelper.canDrawOverlays(this@OverlayService)) {

                    overlayManager?.destroy()

                    return@collectLatest

                }

                overlayManager?.applySettings(settings)
                if (previewActive) {
                    overlayManager?.setPreviewMode(true, previewContent)
                }

            }

        }

    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_RELOAD_APPS -> overlayManager?.reloadApps()
            ACTION_PREVIEW_START -> {
                previewActive = true
                previewContent = intent?.getStringExtra(EXTRA_PREVIEW_CONTENT)
                    ?.let { runCatching { LayoutPreviewContent.valueOf(it) }.getOrNull() }
                    ?: LayoutPreviewContent.TRIGGER_ONLY
                overlayManager?.setPreviewMode(true, previewContent)
            }
            ACTION_PREVIEW_STOP -> {
                previewActive = false
                overlayManager?.setPreviewMode(false)
            }
        }

        return START_STICKY

    }



    override fun onDestroy() {

        OverlayService.foregroundPackage = null
        foregroundTracker?.stop()

        foregroundTracker = null

        overlayManager?.destroy()

        overlayManager = null

        serviceScope.cancel()

        super.onDestroy()

    }



    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)



    private fun createNotificationChannel() {

        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(

            CHANNEL_ID,

            getString(R.string.app_name),

            NotificationManager.IMPORTANCE_LOW,

        )

        manager.createNotificationChannel(channel)

    }



    private fun buildNotification(): Notification {

        val intent = PendingIntent.getActivity(

            this,

            0,

            Intent(this, MainActivity::class.java),

            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,

        )

        return NotificationCompat.Builder(this, CHANNEL_ID)

            .setContentTitle(getString(R.string.service_notification_title))

            .setContentText(getString(R.string.service_notification_text))

            .setSmallIcon(R.drawable.ic_notification)

            .setContentIntent(intent)

            .setOngoing(true)

            .build()

    }



    companion object {

        const val ACTION_RELOAD_APPS = "com.slideindex.app.RELOAD_APPS"
        const val ACTION_PREVIEW_START = "com.slideindex.app.PREVIEW_START"
        const val ACTION_PREVIEW_STOP = "com.slideindex.app.PREVIEW_STOP"
        const val EXTRA_PREVIEW_CONTENT = "preview_content"

        @Volatile
        var foregroundPackage: String? = null

        @Volatile
        var gestureForegroundPackage: String? = null

        fun captureGestureForegroundPackage() {
            gestureForegroundPackage = foregroundPackage
        }

        private const val CHANNEL_ID = "slide_index_service"

        private const val NOTICE_ID = 1001

        private const val NOTIFICATION_ID = NOTICE_ID

    }

}

