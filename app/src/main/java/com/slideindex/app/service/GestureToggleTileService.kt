package com.slideindex.app.service

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.slideindex.app.R
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.util.ServiceEnabledStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class GestureToggleTileService : TileService() {

    @Inject
    lateinit var deps: AppDependencies

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settingsJob: Job? = null
    private var isServiceEnabled = false

    @Volatile
    private var pendingUserToggle = false

    @Volatile
    private var lastClickUptimeMs = 0L

    override fun onTileAdded() {
        super.onTileAdded()
        Log.i(LOG_TAG, "onTileAdded")
        GestureToggleTileWarmup.requestListening(this, "onTileAdded")
    }

    override fun onStartListening() {
        super.onStartListening()
        settingsJob?.cancel()
        if (!pendingUserToggle) {
            val enabled = readCurrentEnabled()
            isServiceEnabled = enabled
            updateTileState(enabled)
            logTileState("onStartListening")
        }
        settingsJob = deps.settingsRepository.settings
            .map { it.serviceEnabled }
            .distinctUntilChanged()
            .onEach { enabled ->
                if (pendingUserToggle) return@onEach
                isServiceEnabled = enabled
                updateTileState(enabled)
            }
            .launchIn(serviceScope)
    }

    override fun onStopListening() {
        settingsJob?.cancel()
        settingsJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        lastClickUptimeMs = SystemClock.uptimeMillis()
        Log.i(
            LOG_TAG,
            "onClick: thread=${Thread.currentThread().name} locked=$isLocked uptime=$lastClickUptimeMs",
        )
        val action = Runnable { handleClick() }
        if (isLocked) {
            Log.i(LOG_TAG, "onClick: dispatch unlockAndRun")
            unlockAndRun(action)
        } else {
            Log.i(LOG_TAG, "onClick: dispatch mainHandler.post")
            mainHandler.post(action)
        }
    }

    private fun handleClick() {
        val elapsedMs = SystemClock.uptimeMillis() - lastClickUptimeMs
        val current = readCurrentEnabled()
        val newState = !current
        Log.i(
            LOG_TAG,
            "handleClick: elapsedSinceOnClick=${elapsedMs}ms current=$current newState=$newState " +
                "a11yConnected=${SlideIndexAccessibilityService.isConnected()}",
        )
        logTileState("handleClick/beforeFastPath")
        pendingUserToggle = true
        ServiceEnabledStore.write(applicationContext, newState)
        isServiceEnabled = newState
        updateTileState(newState)
        SlideIndexAccessibilityService.applyServiceEnabledImmediate(newState)
        Log.i(LOG_TAG, "handleClick: fastPath done elapsed=${SystemClock.uptimeMillis() - lastClickUptimeMs}ms")

        serviceScope.launch {
            val persistStartMs = SystemClock.uptimeMillis()
            try {
                withContext(Dispatchers.IO) {
                    deps.settingsRepository.setServiceEnabled(newState)
                    OverlayServiceLifecycle.syncFromSettings(
                        applicationContext,
                        deps.settingsRepository,
                    )
                }
                Log.i(
                    LOG_TAG,
                    "handleClick: persist done persistMs=${SystemClock.uptimeMillis() - persistStartMs} " +
                        "totalMs=${SystemClock.uptimeMillis() - lastClickUptimeMs}",
                )
            } catch (error: Exception) {
                Log.e(LOG_TAG, "handleClick: persist failed after ${SystemClock.uptimeMillis() - persistStartMs}ms", error)
            } finally {
                pendingUserToggle = false
            }
        }
    }

    private fun readCurrentEnabled(): Boolean {
        val context = applicationContext
        val fromMirror = ServiceEnabledStore.hasPersistedValue(context)
        val enabled = if (fromMirror) {
            ServiceEnabledStore.read(context)
        } else {
            qsTile?.state == Tile.STATE_ACTIVE
        }
        Log.d(
            LOG_TAG,
            "readCurrentEnabled: enabled=$enabled fromMirror=$fromMirror " +
                "qsTileState=${qsTile?.state} isServiceEnabled=$isServiceEnabled",
        )
        return enabled
    }

    private fun logTileState(stage: String) {
        Log.d(
            LOG_TAG,
            "$stage: isServiceEnabled=$isServiceEnabled mirrorHasValue=" +
                "${ServiceEnabledStore.hasPersistedValue(applicationContext)} " +
                "mirror=${ServiceEnabledStore.read(applicationContext)} qsTileState=${qsTile?.state}",
        )
    }

    private fun updateTileState(enabled: Boolean) {
        val tile = qsTile
        if (tile == null) {
            Log.w(LOG_TAG, "updateTileState: qsTile is null, enabled=$enabled")
            return
        }
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_gesture_switch)
        tile.updateTile()
        Log.d(LOG_TAG, "updateTileState: enabled=$enabled state=${tile.state}")
    }

    internal companion object {
        const val LOG_TAG = "GestureToggleTile"
    }
}
