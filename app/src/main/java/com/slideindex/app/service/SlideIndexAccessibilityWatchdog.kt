package com.slideindex.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.PowerManager
import com.slideindex.app.overlay.EdgeOverlayHost
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
                    overlayHost()?.refreshTriggerVisibility()
                }
                Intent.ACTION_SCREEN_ON -> {
                    syncLockScreenState()
                    overlayHost()?.refreshTriggerVisibility()
                }
                Intent.ACTION_USER_PRESENT -> {
                    TriggerEnvironmentState.lockScreenActive = false
                    overlayHost()?.refreshTriggerVisibility()
                }
            }
        }
    }

    fun syncLockScreenState() {
        val accessibilityWindows = service.windows
        TriggerEnvironmentState.lockScreenActive =
            LockScreenState.detectActive(service, accessibilityWindows)
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
        mainHandler.postDelayed({
            service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        }, SCREENSHOT_DELAY_MS)
    }

    companion object {
        private const val SCREENSHOT_DELAY_MS = 500L
    }
}
