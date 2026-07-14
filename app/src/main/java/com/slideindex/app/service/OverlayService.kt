package com.slideindex.app.service

import com.slideindex.app.di.AppDependencies
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.MainActivity
import com.slideindex.app.R
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.LayoutPreviewFocus
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.shake.ShakeGestureHost
import com.slideindex.app.util.SecureSettingsHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service for persistent notification and settings sync.
 * Edge overlays are hosted by [SlideIndexAccessibilityService] (SideGesture-style).
 */
@dagger.hilt.android.AndroidEntryPoint
class OverlayService : LifecycleService() {

    @javax.inject.Inject lateinit var deps: AppDependencies
    @javax.inject.Inject lateinit var shakeGestureHost: ShakeGestureHost

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promoteToForeground()
        shakeGestureHost.start(lifecycleScope)
        startAccessibilityWatchdog()
    }

    private fun startAccessibilityWatchdog() {
        lifecycleScope.launch {
            while (isActive) {
                delay(ACCESSIBILITY_WATCHDOG_INTERVAL_MS)
                val deps = this@OverlayService.deps
                val settings = deps.settingsRepository.settings.first()
                if (!settings.accessibilityKeepAliveEnabled) continue
                if (!settings.serviceEnabled) continue
                if (!SecureSettingsHelper.hasWriteSecureSettings(this@OverlayService)) continue
                SecureSettingsHelper.ensureAccessibilityEnabled(this@OverlayService)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // startForegroundService() requires startForeground() on every delivery, not only in onCreate().
        promoteToForeground()
        when (intent?.action) {
            ACTION_RELOAD_APPS -> SlideIndexAccessibilityService.reloadApps()
            ACTION_PREVIEW_START -> {
                val content = intent.getStringExtra(EXTRA_PREVIEW_CONTENT)
                    ?.let { runCatching { LayoutPreviewContent.valueOf(it) }.getOrNull() }
                    ?: LayoutPreviewContent.TRIGGER_ONLY
                val focus = intent.parsePreviewFocus()
                SlideIndexAccessibilityService.setPreviewMode(true, content, focus)
            }
            ACTION_PREVIEW_STOP -> SlideIndexAccessibilityService.setPreviewMode(false)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        shakeGestureHost.stop()
        super.onDestroy()
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

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
        const val EXTRA_PREVIEW_FOCUS_SIDE = "preview_focus_side"
        const val EXTRA_PREVIEW_HANDLE_ID = "preview_focus_handle_id"
        const val EXTRA_PREVIEW_SHOW_SWIPE_DISTANCES = "preview_show_swipe_distances"
        const val EXTRA_PREVIEW_SHOW_PAIRED_GROUP = "preview_show_paired_group"

        internal fun Intent.parsePreviewFocus(): LayoutPreviewFocus? {
            val sideRaw = getStringExtra(EXTRA_PREVIEW_FOCUS_SIDE) ?: return null
            val handleId = getStringExtra(EXTRA_PREVIEW_HANDLE_ID) ?: return null
            val side = when (sideRaw.uppercase()) {
                "LEFT" -> PanelSide.LEFT
                "RIGHT" -> PanelSide.RIGHT
                else -> return null
            }
            return LayoutPreviewFocus(
                side = side,
                handleId = handleId,
                showSwipeDistances = getBooleanExtra(EXTRA_PREVIEW_SHOW_SWIPE_DISTANCES, false),
                showPairedGroup = getBooleanExtra(EXTRA_PREVIEW_SHOW_PAIRED_GROUP, false),
            )
        }

        @Volatile
        var foregroundPackage: String? = null

        @Volatile
        var gestureForegroundPackage: String? = null

        fun captureGestureForegroundPackage() {
            gestureForegroundPackage = foregroundPackage
        }

        private const val CHANNEL_ID = "slide_index_service"
        private const val NOTIFICATION_ID = 1001
        private const val ACCESSIBILITY_WATCHDOG_INTERVAL_MS = 60_000L
    }
}
