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
class ShakeGestureHost @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val runtimePort: ShakeRuntimePort,
    private val actionPort: ShakeActionPort,
    private val feedbackPort: ShakeFeedbackPort,
) {
    private var detector: ShakeGestureDetector? = null
    private var settingsJob: Job? = null
    private var hostScope: CoroutineScope? = null
    private var latestSettings: AppSettings? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenReceiverRegistered = false
    private var screenInteractive = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenInteractive = true
                    latestSettings?.let(::applyRuntime)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractive = false
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
        hostScope = scope
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
        hostScope = null
        unregisterScreenReceiver()
        detector?.stop()
        detector = null
        feedbackPort.detachFeedbackOverlay()
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
        val shake = settings.shakeGestureSettings
        val accessibilityEnabled = runtimePort.isAccessibilityServiceEnabled()
        val lockScreenActive = runtimePort.isLockScreenActive()
        val shouldRun = shake.enabled &&
            accessibilityEnabled &&
            screenInteractive &&
            (!lockScreenActive || shake.lockScreenShakeEnabled)

        if (!shouldRun) {
            if (detector != null) {
                val reason = when {
                    !shake.enabled -> "shake disabled"
                    !accessibilityEnabled -> "accessibility disabled"
                    !screenInteractive -> "screen not interactive"
                    lockScreenActive && !shake.lockScreenShakeEnabled -> "lock screen shake disabled"
                    else -> "unknown"
                }
                Log.d(TAG, "stopping detector: $reason")
            }
            detector?.stop()
            detector = null
            return
        }

        val activeDetector = detector ?: ShakeGestureDetector(appContext) { type ->
            if (Looper.myLooper() == Looper.getMainLooper()) {
                handleGestureDetected(type)
            } else {
                mainHandler.post { handleGestureDetected(type) }
            }
        }.also { detector = it }

        if (!activeDetector.isAvailable) {
            Log.w(TAG, "stopping detector: no gyroscope or accelerometer")
            activeDetector.stop()
            detector = null
            return
        }

        activeDetector.setSensitivity(
            global = shake.globalSensitivity,
            independentEnabled = shake.independentSensitivityEnabled,
            perDirection = shake.perDirectionSensitivity,
        )
        activeDetector.start()
        Log.d(
            TAG,
            "detector running enabled=${shake.enabled} sensitivity=${shake.globalSensitivity}",
        )
    }

    private fun handleGestureDetected(type: ShakeGestureType) {
        runCatching {
            handleGestureDetectedInternal(type)
        }.onFailure { error ->
            Log.e(TAG, "handle shake gesture failed: $type", error)
        }
    }

    private fun handleGestureDetectedInternal(type: ShakeGestureType) {
        val settings = latestSettings ?: run {
            Log.d(TAG, "gesture $type blocked: settings not loaded")
            return
        }
        val shake = settings.shakeGestureSettings
        if (!shake.enabled) {
            Log.d(TAG, "gesture $type blocked: shake disabled")
            return
        }

        if (!powerManager().isInteractive) {
            Log.d(TAG, "gesture $type blocked: screen not interactive")
            return
        }

        val foregroundPackage = runtimePort.foregroundPackage()
        if (foregroundPackage != null && foregroundPackage in shake.blacklistedPackages) {
            Log.d(TAG, "gesture $type blocked: blacklisted package $foregroundPackage")
            return
        }
        if (shake.disableInLandscape && runtimePort.isLandscape()) {
            Log.d(TAG, "gesture $type blocked: landscape disabled")
            return
        }

        val lockScreenActive = runtimePort.isLockScreenActive()
        if (lockScreenActive && !shake.lockScreenShakeEnabled) {
            Log.d(TAG, "gesture $type blocked: lock screen shake disabled")
            return
        }

        val action = resolveShakeAction(shake, type, foregroundPackage, lockScreenActive)
        dispatchAction(action, settings, shake, type)
    }

    private fun dispatchAction(
        action: GestureAction,
        settings: AppSettings,
        shake: ShakeGestureSettings,
        type: ShakeGestureType,
    ) {
        if (action == GestureAction.None) {
            Log.d(TAG, "gesture $type detected, action is None")
            deliverFeedback(shake, type, action)
            return
        }

        Log.i(TAG, "gesture $type executing action=$action")
        runtimePort.captureGestureForegroundPackage()

        if (requiresAccessibilityService(action)) {
            dispatchAccessibilityAction(action, type, shake, attempt = 0)
            return
        }

        runCatching {
            val ok = actionPort.execute(
                action = action,
                settings = settings,
                anchorRawX = runtimePort.screenCenterX(),
                anchorRawY = runtimePort.screenCenterY(),
            )
            if (ok) {
                Log.i(TAG, "gesture $type action $action succeeded")
            } else {
                Log.w(TAG, "gesture $type action $action returned false")
            }
        }.onFailure { error ->
            Log.e(TAG, "execute shake action failed: $action", error)
        }
        deliverFeedback(shake, type, action)
    }

    private fun dispatchAccessibilityAction(
        action: GestureAction,
        type: ShakeGestureType,
        shake: ShakeGestureSettings,
        attempt: Int,
    ) {
        val ok = runtimePort.performAccessibilityAction(action)
        if (ok) {
            deliverFeedback(shake, type, action)
            Log.i(TAG, "gesture $type action $action succeeded")
            return
        }
        if (attempt < 2) {
            Log.d(TAG, "gesture $type action $action retry ${attempt + 1}")
            mainHandler.postDelayed({
                dispatchAccessibilityAction(action, type, shake, attempt + 1)
            }, 100L)
            return
        }
        Log.w(
            TAG,
            "gesture $type action $action FAILED — accessibility connected=${runtimePort.isAccessibilityConnected()}",
        )
    }

    private fun deliverFeedback(
        shake: ShakeGestureSettings,
        type: ShakeGestureType,
        action: GestureAction,
    ) {
        if (shake.vibrationFeedbackEnabled) {
            feedbackPort.vibrate(appContext)
        }
        if (shake.animationFeedbackEnabled) {
            feedbackPort.showGestureFeedback(appContext, type, action, shake.animationColorArgb)
        }
    }

    private fun requiresAccessibilityService(action: GestureAction): Boolean = when (action) {
        GestureAction.Back,
        GestureAction.Home,
        GestureAction.Recents,
        GestureAction.OpenNotifications,
        GestureAction.OpenQuickSettings,
        GestureAction.LockScreen,
        GestureAction.Screenshot,
        GestureAction.PowerMenu,
        GestureAction.PreviousApp,
        GestureAction.KeepScreenOn,
        GestureAction.ScrollToTop,
        GestureAction.ScrollToBottom,
        -> true
        else -> false
    }

    private fun powerManager(): PowerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    companion object {
        private const val TAG = "ShakeGestureHost"
    }
}
