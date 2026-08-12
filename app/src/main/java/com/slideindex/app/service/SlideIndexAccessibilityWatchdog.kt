package com.slideindex.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.PowerManager
import android.view.Choreographer
import com.slideindex.app.overlay.EdgeOverlayHost
import com.slideindex.app.overlay.FloatBallOverlay
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.FloatingPointerOverlayWindow
import com.slideindex.app.overlay.GlobalOverlayDismissHelper
import com.slideindex.app.util.LockScreenState
import com.slideindex.app.util.TriggerEnvironmentState

internal class SlideIndexAccessibilityWatchdog(
    private val service: SlideIndexAccessibilityService,
    private val overlayHost: () -> EdgeOverlayHost?,
) {
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenLockReceiverRegistered = false

    private val screenLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    TriggerEnvironmentState.lockScreenActive = true
                    GlobalOverlayDismissHelper.dismissAllPanels()
                    // 触钮 + 悬浮球/边角轮盘一并按锁屏抑制策略刷新，避免解锁后只恢复触钮。
                    overlayHost()?.refreshOverlaySuppression()
                }
                Intent.ACTION_SCREEN_ON -> {
                    syncLockScreenState()
                    overlayHost()?.refreshOverlaySuppression()
                }
                Intent.ACTION_USER_PRESENT -> {
                    TriggerEnvironmentState.lockScreenActive = false
                    overlayHost()?.refreshOverlaySuppression()
                }
            }
        }
    }

    fun syncLockScreenState() {
        val accessibilityWindows = service.windows
        val isLocked = LockScreenState.detectActive(service, accessibilityWindows)
        TriggerEnvironmentState.lockScreenActive = isLocked
        if (isLocked) {
            GlobalOverlayDismissHelper.dismissAllPanels()
        }
    }

    fun registerScreenLockReceiver() {
        if (screenLockReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        service.registerReceiver(screenLockReceiver, filter)
        screenLockReceiverRegistered = true
    }

    fun unregisterScreenLockReceiver() {
        if (!screenLockReceiverRegistered) return
        runCatching { service.unregisterReceiver(screenLockReceiver) }
        screenLockReceiverRegistered = false
        TriggerEnvironmentState.lockScreenActive = false
    }

    fun toggleKeepScreenOn(): Boolean {
        if (wakeLock != null) {
            wakeLock?.release()
            wakeLock = null
            return true
        }
        val powerManager = service.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        @Suppress("DEPRECATION")
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "SlideIndex:KeepScreenOn",
        )
        return runCatching {
            wakeLock?.acquire(10 * 60 * 1000L)
            true
        }.getOrDefault(false)
    }

    fun releaseWakeLock() {
        wakeLock?.release()
        wakeLock = null
    }

    fun takeScreenshotDelayed(mainHandler: Handler) {
        val hideOverlays = Runnable {
            FloatingPointerOverlayWindow.suppressForScreenshotCapture()
            FloatBallOverlay.suppressForScreenshotCapture()
            FloatBallPickResultPanel.suppressForScreenshotCapture()
        }
        val scheduleCapture = Runnable {
            mainHandler.postDelayed({
                service.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT,
                )
                mainHandler.postDelayed({
                    FloatingPointerOverlayWindow.restoreAfterScreenshotCapture()
                    FloatBallOverlay.restoreAfterScreenshotCapture()
                    FloatBallPickResultPanel.restoreAfterScreenshotCapture()
                }, SCREENSHOT_RESTORE_DELAY_MS)
            }, SCREENSHOT_DELAY_MS)
        }
        val hideThenCapture = Runnable {
            hideOverlays.run()
            // Wait one compositor frame so WM detach is visible before system capture.
            Choreographer.getInstance().postFrameCallback { scheduleCapture.run() }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            hideThenCapture.run()
        } else {
            mainHandler.post(hideThenCapture)
        }
    }

    companion object {
        private const val SCREENSHOT_DELAY_MS = 400L
        /** Keep overlays hidden until the system capture finishes. */
        private const val SCREENSHOT_RESTORE_DELAY_MS = 800L
    }
}
