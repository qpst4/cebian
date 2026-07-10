package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slideindex.app.notification.NotificationListenerPort
import com.slideindex.app.util.BrightnessControlHelper
import com.slideindex.app.util.ContinuousAdjustController
import com.slideindex.app.util.FlashlightHelper
import com.slideindex.app.util.MediaSessionHelper
import com.slideindex.app.util.MediaSessionTracker
import com.slideindex.app.util.QuickToolsHelper
import com.slideindex.app.util.ScreenRecordHelper
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.util.VolumeControlHelper

/** One of the 12 circular quick-toggle tiles in the 3x4 grid (see [OhoQuickToolsPanel]). */
enum class OhoTile {
    WIFI,
    MOBILE_DATA,
    SOUND,
    BLUETOOTH,
    AUTO_ROTATE,
    QUICK_PANEL,
    SCREENSHOT,
    LOCK,
    FLASHLIGHT,
    DO_NOT_DISTURB,
    SCREEN_RECORD,
    CLOSE,
}

/** UI-originated interactions the panel doesn't resolve itself; handled by [OhoQuickToolsOverlayWindow]. */
sealed interface OhoPanelEvent {
    data class Tile(val tile: OhoTile) : OhoPanelEvent
    data class TileLongPress(val tile: OhoTile) : OhoPanelEvent
    data object ToggleAutoBrightness : OhoPanelEvent
    data object OpenMediaApp : OhoPanelEvent
    data object MediaPrevious : OhoPanelEvent
    data object MediaPlayPause : OhoPanelEvent
    data object MediaNext : OhoPanelEvent
    data object ChevronUp : OhoPanelEvent
    data object ChevronDown : OhoPanelEvent
}

/**
 * Live, Compose-observable state backing [OhoQuickToolsPanel]. Reads/writes real system state
 * through the same helpers already used by the rest of the app (Wi-Fi/Bluetooth via Shizuku shell,
 * brightness/volume via [ContinuousAdjustController], flashlight via Camera2, DND via NotificationManager).
 */
class OhoQuickToolsPanelState(
    context: Context,
    private val listenerPort: NotificationListenerPort? = null,
) {
    private val appContext = context.applicationContext
    private val continuousAdjust = ContinuousAdjustController(appContext, overlayBrightness = null)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val settingsHandler = Handler(Looper.getMainLooper())

    var brightnessFraction by mutableFloatStateOf(0.5f)
        private set
    var volumeFraction by mutableFloatStateOf(0.5f)
        private set
    var autoBrightnessEnabled by mutableStateOf(false)
        private set
    var mediaAppPackage by mutableStateOf<String?>(null)
        private set
    var mediaIsPlaying by mutableStateOf(false)
        private set
    var mediaListenerEnabled by mutableStateOf(false)
        private set
    var ringerMode by mutableIntStateOf(AudioManager.RINGER_MODE_NORMAL)
        private set

    private val activeStates = mutableStateMapOf<OhoTile, Boolean>()
    private val mediaTrackerListener: (MediaSessionTracker.Snapshot) -> Unit = { snap ->
        mainHandler.post {
            mediaAppPackage = snap.packageName
            mediaIsPlaying = snap.isPlaying
            mediaListenerEnabled = MediaSessionHelper.isNotificationListenerEnabled(appContext)
            activeStates[OhoTile.SCREEN_RECORD] = ScreenRecordHelper.isRecording
        }
    }
    private var volumeChangeReceiver: BroadcastReceiver? = null
    private var defaultDataSubReceiver: BroadcastReceiver? = null
    private var brightnessObserver: ContentObserver? = null
    private var mobileDataObserver: ContentObserver? = null
    private var mediaPollRunnable: Runnable? = null
    private var slowSyncThread: Thread? = null
    private val torchListener: (Boolean) -> Unit = { enabled ->
        mainHandler.post { activeStates[OhoTile.FLASHLIGHT] = enabled }
    }

    fun isActive(tile: OhoTile): Boolean = when (tile) {
        OhoTile.SCREEN_RECORD -> ScreenRecordHelper.isRecording
        OhoTile.SOUND -> true
        else -> activeStates[tile] == true
    }

    /** Called once right before the panel becomes visible. */
    fun refresh() {
        refreshBrightnessFromSystem()
        refreshVolumeFromSystem()
        refreshRingerModeFromSystem()
        activeStates[OhoTile.AUTO_ROTATE] = QuickToolsHelper.readAutoRotateEnabled(appContext)
        refreshFlashlightFromSystem()
        activeStates[OhoTile.DO_NOT_DISTURB] = VolumeControlHelper.isDndEnabled(appContext)
        activeStates[OhoTile.SCREEN_RECORD] = ScreenRecordHelper.isRecording
        refreshMediaFromSystem()
        refreshSlowStates()
    }

    fun startLiveSync() {
        mediaListenerEnabled = MediaSessionHelper.isNotificationListenerEnabled(appContext)
        MediaSessionTracker.addListener(mediaTrackerListener)
        MediaSessionTracker.refreshIfPossible(appContext, listenerPort)
        FlashlightHelper.startObserving(appContext)
        FlashlightHelper.addListener(torchListener)
        registerVolumeReceiver()
        registerDefaultDataSubReceiver()
        registerBrightnessObserver()
        registerMobileDataObserver()
        startMediaPolling()
    }

    fun stopLiveSync() {
        MediaSessionTracker.removeListener(mediaTrackerListener)
        FlashlightHelper.removeListener(torchListener)
        FlashlightHelper.stopObserving()
        unregisterVolumeReceiver()
        unregisterDefaultDataSubReceiver()
        unregisterBrightnessObserver()
        unregisterMobileDataObserver()
        stopMediaPolling()
        slowSyncThread?.interrupt()
        slowSyncThread = null
    }

    private fun refreshSlowStates() {
        slowSyncThread?.interrupt()
        slowSyncThread = Thread {
            val wifi = QuickToolsHelper.readWifiEnabled(appContext) == true
            val mobileData = QuickToolsHelper.readMobileDataEnabled(appContext) == true
            val bluetooth = QuickToolsHelper.readBluetoothEnabled(appContext) == true
            val mobileSwitch = QuickToolsHelper.readMobileDataSwitchState(appContext)
            if (Thread.currentThread().isInterrupted) return@Thread
            mainHandler.post {
                activeStates[OhoTile.WIFI] = wifi
                activeStates[OhoTile.MOBILE_DATA] = mobileData
                activeStates[OhoTile.BLUETOOTH] = bluetooth
                mobileSwitch?.let { activeStates[OhoTile.MOBILE_DATA] = it }
            }
        }.also { it.start() }
    }

    private fun refreshMobileDataFromSystem() {
        QuickToolsHelper.readMobileDataSwitchState(appContext)?.let { enabled ->
            activeStates[OhoTile.MOBILE_DATA] = enabled
        }
    }

    private fun refreshMobileDataStateAsync() {
        Thread {
            repeat(5) { attempt ->
                if (Thread.currentThread().isInterrupted) return@Thread
                val switch = QuickToolsHelper.readMobileDataSwitchState(appContext)
                if (switch != null) {
                    mainHandler.post { activeStates[OhoTile.MOBILE_DATA] = switch }
                }
                if (attempt < 4) Thread.sleep(350L)
            }
        }.start()
    }

    fun refreshBrightnessFromSystem() {
        brightnessFraction = continuousAdjust.readCurrentFraction(ContinuousAdjustController.Mode.BRIGHTNESS)
        autoBrightnessEnabled = BrightnessControlHelper.readAutoBrightnessEnabled(appContext)
    }

    fun refreshVolumeFromSystem() {
        volumeFraction = continuousAdjust.readCurrentFraction(ContinuousAdjustController.Mode.VOLUME)
    }

    fun refreshRingerModeFromSystem() {
        ringerMode = VolumeControlHelper.readRingerMode(appContext)
    }

    fun refreshFlashlightFromSystem() {
        activeStates[OhoTile.FLASHLIGHT] = FlashlightHelper.isOn()
    }

    fun refreshMediaFromSystem() {
        MediaSessionTracker.refreshIfPossible(appContext, listenerPort)
        val live = MediaSessionTracker.currentSnapshot()
        if (!live.packageName.isNullOrBlank()) {
            mediaAppPackage = live.packageName
            mediaIsPlaying = live.isPlaying
            mediaListenerEnabled = MediaSessionHelper.isNotificationListenerEnabled(appContext)
            return
        }
        Thread {
            val info = MediaSessionHelper.query(appContext)
            if (Thread.currentThread().isInterrupted) return@Thread
            mainHandler.post {
                mediaAppPackage = info?.packageName
                mediaIsPlaying = info?.isPlaying == true
                mediaListenerEnabled = MediaSessionHelper.isNotificationListenerEnabled(appContext)
                activeStates[OhoTile.SCREEN_RECORD] = ScreenRecordHelper.isRecording
            }
        }.start()
    }

    fun toggleAutoBrightness(): Boolean? {
        val enabled = BrightnessControlHelper.toggleAutoBrightness(appContext) ?: return null
        autoBrightnessEnabled = enabled
        refreshBrightnessFromSystem()
        return enabled
    }

    fun updateBrightness(fraction: Float, previewOnly: Boolean) {
        val clamped = fraction.coerceIn(0f, 1f)
        brightnessFraction = clamped
        continuousAdjust.setFraction(ContinuousAdjustController.Mode.BRIGHTNESS, clamped, previewOnly)
    }

    fun commitBrightness() {
        continuousAdjust.setFraction(ContinuousAdjustController.Mode.BRIGHTNESS, brightnessFraction, previewOnly = false)
    }

    fun updateVolume(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        volumeFraction = clamped
        continuousAdjust.setFraction(ContinuousAdjustController.Mode.VOLUME, clamped)
    }

    fun launchMediaApp(): Boolean {
        val pkg = mediaAppPackage ?: return false
        val pm = appContext.packageManager
        val launch = pm.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: pm.getLeanbackLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
        return runCatching { appContext.startActivity(launch) }.isSuccess
    }

    /** Launch the media app, or open notification-access settings when media cannot be resolved. */
    fun openMediaTarget(): Boolean {
        if (launchMediaApp()) return true
        if (!MediaSessionHelper.isNotificationListenerEnabled(appContext)) {
            appContext.startActivity(MediaSessionHelper.notificationListenerSettingsIntent())
        }
        return false
    }

    /** @return true if the panel should dismiss after opening settings. */
    fun onTileLongPress(tile: OhoTile): Boolean = QuickToolsTileSettings.open(appContext, tile)

    /** @return true if the panel should dismiss after handling this tap. */
    fun onTileTap(tile: OhoTile): Boolean {
        when (tile) {
            OhoTile.WIFI -> QuickToolsHelper.toggleWifi(appContext)?.let { activeStates[OhoTile.WIFI] = it }
            OhoTile.MOBILE_DATA -> {
                val hint = activeStates[OhoTile.MOBILE_DATA] == true
                QuickToolsHelper.toggleMobileData(appContext, currentHint = hint)
                    ?.let { activeStates[OhoTile.MOBILE_DATA] = it }
                refreshMobileDataStateAsync()
            }
            OhoTile.SOUND ->
                VolumeControlHelper.cycleRingerMode(appContext)?.let { ringerMode = it }
            OhoTile.BLUETOOTH ->
                QuickToolsHelper.toggleBluetooth(appContext)?.let { activeStates[OhoTile.BLUETOOTH] = it }
            OhoTile.AUTO_ROTATE ->
                QuickToolsHelper.toggleAutoRotate(appContext)?.let { activeStates[OhoTile.AUTO_ROTATE] = it }
            OhoTile.QUICK_PANEL ->
                com.slideindex.app.service.SlideIndexAccessibilityService.perform(
                    com.slideindex.app.gesture.GestureAction.OpenNotifications,
                )
            OhoTile.SCREENSHOT ->
                com.slideindex.app.service.SlideIndexAccessibilityService.perform(
                    com.slideindex.app.gesture.GestureAction.Screenshot,
                )
            OhoTile.LOCK ->
                com.slideindex.app.service.SlideIndexAccessibilityService.perform(
                    com.slideindex.app.gesture.GestureAction.LockScreen,
                )
            OhoTile.FLASHLIGHT -> FlashlightHelper.toggle(appContext)
            OhoTile.DO_NOT_DISTURB ->
                VolumeControlHelper.toggleDnd(appContext)?.let {
                    activeStates[OhoTile.DO_NOT_DISTURB] = VolumeControlHelper.isDndFilter(it)
                }
            OhoTile.SCREEN_RECORD -> {
                ScreenRecordHelper.toggle(appContext)
                activeStates[OhoTile.SCREEN_RECORD] = ScreenRecordHelper.isRecording
            }
            OhoTile.CLOSE -> closeCurrentForegroundApp()
        }
        return tile in dismissingTiles
    }

    private fun closeCurrentForegroundApp() {
        if (!TaskManagerUtil.hasPermission()) return
        Thread { TaskManagerUtil.removeCurrentFrontAppTask() }.start()
    }

    private fun registerVolumeReceiver() {
        if (volumeChangeReceiver != null) return
        volumeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    VOLUME_CHANGED_ACTION -> {
                        val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                        if (streamType == AudioManager.STREAM_MUSIC) {
                            refreshVolumeFromSystem()
                        }
                    }
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> refreshRingerModeFromSystem()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(VOLUME_CHANGED_ACTION)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(volumeChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(volumeChangeReceiver, filter)
        }
    }

    private fun unregisterVolumeReceiver() {
        volumeChangeReceiver?.let { receiver ->
            runCatching { appContext.unregisterReceiver(receiver) }
        }
        volumeChangeReceiver = null
    }

    private fun registerDefaultDataSubReceiver() {
        if (defaultDataSubReceiver != null) return
        defaultDataSubReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshMobileDataFromSystem()
                refreshMobileDataStateAsync()
            }
        }
        val filter = IntentFilter(ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(defaultDataSubReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(defaultDataSubReceiver, filter)
        }
    }

    private fun unregisterDefaultDataSubReceiver() {
        defaultDataSubReceiver?.let { receiver ->
            runCatching { appContext.unregisterReceiver(receiver) }
        }
        defaultDataSubReceiver = null
    }

    private fun registerMobileDataObserver() {
        if (mobileDataObserver != null) return
        val observer = object : ContentObserver(settingsHandler) {
            override fun onChange(selfChange: Boolean) {
                refreshMobileDataFromSystem()
            }
        }
        mobileDataObserver = observer
        appContext.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(MOBILE_DATA_SETTING_KEY),
            false,
            observer,
        )
    }

    private fun unregisterMobileDataObserver() {
        mobileDataObserver?.let { observer ->
            runCatching { appContext.contentResolver.unregisterContentObserver(observer) }
        }
        mobileDataObserver = null
    }

    private fun registerBrightnessObserver() {
        if (brightnessObserver != null) return
        val observer = object : ContentObserver(settingsHandler) {
            override fun onChange(selfChange: Boolean) {
                refreshBrightnessFromSystem()
            }
        }
        brightnessObserver = observer
        val resolver = appContext.contentResolver
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            false,
            observer,
        )
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false,
            observer,
        )
    }

    private fun unregisterBrightnessObserver() {
        brightnessObserver?.let { observer ->
            runCatching { appContext.contentResolver.unregisterContentObserver(observer) }
        }
        brightnessObserver = null
    }

    private fun startMediaPolling() {
        if (mediaPollRunnable != null) return
        val runnable = object : Runnable {
            override fun run() {
                refreshMediaFromSystem()
                mainHandler.postDelayed(this, MEDIA_POLL_MS)
            }
        }
        mediaPollRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun stopMediaPolling() {
        mediaPollRunnable?.let { mainHandler.removeCallbacks(it) }
        mediaPollRunnable = null
    }

    private companion object {
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val MEDIA_POLL_MS = 500L
        const val MOBILE_DATA_SETTING_KEY = "mobile_data1"
        const val ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED =
            "android.telephony.action.DEFAULT_DATA_SUBSCRIPTION_CHANGED"
        val dismissingTiles = setOf(
            OhoTile.QUICK_PANEL,
            OhoTile.SCREENSHOT,
            OhoTile.LOCK,
            OhoTile.CLOSE,
        )
    }
}
