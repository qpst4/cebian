package com.slideindex.app.backtap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.shake.ShakeActionPort
import com.slideindex.app.shake.ShakeRuntimePort
import com.slideindex.app.shake.ShakeVibrationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class BackTapGestureHost @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val actionPort: ShakeActionPort,
    private val runtimePort: ShakeRuntimePort,
) {
    private var detector: BackTapDetector? = null
    private var settingsJob: Job? = null
    private var latestSettings: AppSettings? = null
    private var screenInteractive = true
    private var charging = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    screenInteractive = true
                    detector?.setScreenOn(true)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractive = false
                    detector?.setScreenOn(false)
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    charging = true
                    updatePauseState()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    charging = false
                    updatePauseState()
                }
            }
        }
    }

    fun start(scope: CoroutineScope) {
        if (settingsJob != null) return
        screenInteractive = powerManager().isInteractive
        registerReceivers()
        settingsJob = scope.launch {
            settingsRepository.settings.collectLatest { settings ->
                latestSettings = settings
                applyRuntime(settings)
            }
        }
    }

    fun stop() {
        settingsJob?.cancel()
        settingsJob = null
        unregisterReceivers()
        detector?.stop()
        detector = null
    }

    private fun applyRuntime(settings: AppSettings) {
        val backTap = settings.backTapSettings
        val shouldRun = backTap.enabled && settings.serviceEnabled
        if (!shouldRun) {
            detector?.stop()
            detector = null
            return
        }
        detector?.stop()
        detector = BackTapDetector(appContext) {
            mainHandler.post {
                val current = latestSettings ?: return@post
                triggerAction(current.backTapSettings.action, current)
            }
        }.also {
            it.setMode(backTap.mode)
            it.setScreenOn(screenInteractive)
            it.start(backTap.sensitivity, backTap.range)
        }
        updatePauseState()
    }

    private fun updatePauseState() {
        val backTap = latestSettings?.backTapSettings ?: return
        val shouldPause = backTap.pauseWhileCharging && charging
        val current = detector ?: return
        if (shouldPause) current.pause() else current.resume()
    }

    private fun triggerAction(action: GestureAction, settings: AppSettings) {
        if (action == GestureAction.None) return
        if (settings.backTapSettings.vibrationFeedbackEnabled) {
            ShakeVibrationHelper.vibrate(appContext)
        }
        actionPort.execute(
            action = action,
            settings = settings,
            anchorRawX = runtimePort.screenCenterX(),
            anchorRawY = runtimePort.screenCenterY(),
        )
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        appContext.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun unregisterReceivers() {
        runCatching { appContext.unregisterReceiver(screenReceiver) }
    }

    private fun powerManager(): PowerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
}
