package com.slideindex.app.shake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class FaceDownGestureHost @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val runtimePort: ShakeRuntimePort,
    private val actionPort: ShakeActionPort,
    private val feedbackPort: ShakeFeedbackPort,
) {
    private var detector: FaceDownDetector? = null
    private var settingsJob: Job? = null
    private var latestSettings: AppSettings? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenReceiverRegistered = false
    private var screenInteractive = true
    private var cooldownActive = false
    private var cooldownRunnable: Runnable? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenInteractive = true
                    latestSettings?.let(::applyRuntime)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractive = false
                    cancelCooldown()
                    latestSettings?.let(::applyRuntime)
                }
                Intent.ACTION_USER_PRESENT -> {
                    latestSettings?.let(::applyRuntime)
                }
            }
        }
    }

    fun start(scope: CoroutineScope) {
        if (settingsJob != null) return
        screenInteractive = powerManager().isInteractive
        registerScreenReceiver()
        settingsJob = scope.launch {
            settingsRepository.settings.collectLatest { settings ->
                latestSettings = settings
                applyRuntime(settings)
            }
        }
        Log.d(TAG, "started (interactive=$screenInteractive)")
    }

    fun stop() {
        settingsJob?.cancel()
        settingsJob = null
        cancelCooldown()
        unregisterScreenReceiver()
        detector?.stop()
        detector = null
        Log.d(TAG, "stopped")
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        appContext.registerReceiver(screenReceiver, filter)
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { appContext.unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }

    private fun applyRuntime(settings: AppSettings) {
        if (cooldownActive) return

        val faceDown = settings.faceDownGestureSettings
        val shake = settings.shakeGestureSettings
        val accessibilityEnabled = runtimePort.isAccessibilityServiceEnabled()
        val shouldRun = faceDown.enabled &&
            accessibilityEnabled &&
            screenInteractive

        if (!shouldRun) {
            if (detector != null) {
                val reason = when {
                    !faceDown.enabled -> "face-down disabled"
                    !accessibilityEnabled -> "accessibility disabled"
                    !screenInteractive -> "screen not interactive"
                    else -> "unknown"
                }
                Log.d(TAG, "stopping detector: $reason")
            }
            detector?.stop()
            detector = null
            return
        }

        val activeDetector = detector ?: FaceDownDetector(appContext) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                handleFaceDownDetected()
            } else {
                mainHandler.post { handleFaceDownDetected() }
            }
        }.also { detector = it }

        if (!activeDetector.isAvailable) {
            Log.w(TAG, "stopping detector: no accelerometer")
            activeDetector.stop()
            detector = null
            return
        }

        activeDetector.configure(
            holdDurationMs = faceDown.holdDurationMs,
            requireProximity = faceDown.requireProximity,
        )
        activeDetector.start()
        Log.d(
            TAG,
            "detector running hold=${faceDown.holdDurationMs}ms " +
                "requireProximity=${faceDown.requireProximity}",
        )
    }

    private fun handleFaceDownDetected() {
        runCatching {
            handleFaceDownDetectedInternal()
        }.onFailure { error ->
            Log.e(TAG, "handle face-down failed", error)
        }
    }

    private fun handleFaceDownDetectedInternal() {
        val settings = latestSettings ?: return
        val faceDown = settings.faceDownGestureSettings
        val shake = settings.shakeGestureSettings
        if (!faceDown.enabled) return
        if (!powerManager().isInteractive) return

        detector?.stop()
        detector = null

        val foregroundPackage = runtimePort.foregroundPackage()
        if (foregroundPackage != null && foregroundPackage in shake.blacklistedPackages) {
            Log.d(TAG, "face-down blocked: blacklisted package $foregroundPackage")
            scheduleCooldown(faceDown.cooldownMs)
            return
        }
        if (faceDown.disableInLandscape && runtimePort.isLandscape()) {
            Log.d(TAG, "face-down blocked: landscape disabled")
            scheduleCooldown(faceDown.cooldownMs)
            return
        }

        val action = faceDown.action
        if (action == GestureAction.None) {
            scheduleCooldown(faceDown.cooldownMs)
            return
        }

        Log.i(TAG, "face-down executing action=$action")
        runtimePort.captureGestureForegroundPackage()

        runCatching {
            val ok = actionPort.execute(
                action = action,
                settings = settings,
                anchorRawX = runtimePort.screenCenterX(),
                anchorRawY = runtimePort.screenCenterY(),
            )
            if (ok) {
                Log.i(TAG, "face-down action $action succeeded")
            } else {
                Log.w(TAG, "face-down action $action returned false")
            }
        }.onFailure { error ->
            Log.e(TAG, "execute face-down action failed: $action", error)
        }

        if (faceDown.vibrationFeedbackEnabled) {
            feedbackPort.vibrate(appContext)
        }

        scheduleCooldown(faceDown.cooldownMs)
    }

    private fun scheduleCooldown(cooldownMs: Long) {
        cancelCooldown()
        cooldownActive = true
        val delayMs = FaceDownGestureSettings.clampCooldownMs(cooldownMs)
        val runnable = Runnable {
            cooldownActive = false
            cooldownRunnable = null
            latestSettings?.let(::applyRuntime)
        }
        cooldownRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelCooldown() {
        cooldownRunnable?.let { mainHandler.removeCallbacks(it) }
        cooldownRunnable = null
        cooldownActive = false
    }

    private fun powerManager(): PowerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    companion object {
        private const val TAG = "FaceDownGestureHost"
    }
}
