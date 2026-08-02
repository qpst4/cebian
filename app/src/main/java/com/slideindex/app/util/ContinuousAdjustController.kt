package com.slideindex.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlin.math.roundToInt
import java.util.concurrent.Executors

import android.os.Build
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ContinuousAdjustController(
    private val context: Context,
    private val overlayBrightness: OverlayBrightnessControl?,
) {
    enum class Mode {
        VOLUME,
        BRIGHTNESS,
    }

    private val appContext = context.applicationContext

    private var activeMode: Mode? = null
    private var anchorRawY = Float.NaN
    private var baselineFraction = 0f
    private var lastFraction = Float.NaN
    private var lastCommittedBrightnessLevel = Int.MIN_VALUE
    private val brightnessWriteExecutor = Executors.newSingleThreadExecutor()
    private val pendingBrightnessFraction = AtomicReference<Float?>(null)
    private val brightnessWriteRunning = AtomicBoolean(false)
    private var lastBrightnessWriteUptimeMs = 0L
    private var manualBrightnessEnsured = false

    fun begin(mode: Mode, rawY: Float): Boolean {
        if (!hasAccess(mode)) return false
        if (activeMode != mode) {
            activeMode = mode
            anchorRawY = rawY
            lastCommittedBrightnessLevel = Int.MIN_VALUE
            manualBrightnessEnsured = false
            baselineFraction = readCurrentFraction(mode)
            lastFraction = baselineFraction
        }
        return true
    }

    fun update(mode: Mode, rawY: Float) {
        if (activeMode != mode) return
        applyFraction(mode, fractionFor(rawY))
    }

    fun applyOnce(mode: Mode, anchorRawY: Float, targetRawY: Float): Float? {
        if (!begin(mode, anchorRawY)) return null
        update(mode, targetRawY)
        val fraction = currentFraction()
        end()
        return fraction
    }

    fun end() {
        val mode = activeMode ?: return
        val fraction = lastFraction
        activeMode = null
        anchorRawY = Float.NaN
        lastFraction = Float.NaN
        lastCommittedBrightnessLevel = Int.MIN_VALUE
        manualBrightnessEnsured = false
        if (mode == Mode.BRIGHTNESS && !fraction.isNaN()) {
            val commitFraction = fraction
            if (BrightnessControlHelper.hasAccess(appContext)) {
                commitBrightnessImmediately(commitFraction)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    clearBrightnessPreview()
                }
            } else {
                clearBrightnessPreview()
            }
        } else {
            clearBrightnessPreview()
        }
    }

    fun readCurrentFraction(mode: Mode): Float = when (mode) {
        Mode.VOLUME -> VolumeControlHelper.readFraction(appContext, VolumeControlHelper.Stream.MEDIA)
        Mode.BRIGHTNESS -> readBrightnessFraction()
    }

    fun clearBrightnessPreview() {
        overlayBrightness?.apply(null)
    }

    fun currentMode(): Mode? = activeMode

    fun currentFraction(): Float =
        if (lastFraction.isNaN()) 0f else lastFraction.coerceIn(0f, 1f)

    fun setFraction(mode: Mode, fraction: Float, previewOnly: Boolean = false) {
        if (!hasAccess(mode)) return
        val clamped = fraction.coerceIn(0f, 1f)
        lastFraction = clamped
        when (mode) {
            Mode.VOLUME ->
                VolumeControlHelper.setFraction(
                    appContext,
                    VolumeControlHelper.Stream.MEDIA,
                    clamped,
                )
            Mode.BRIGHTNESS -> {
                if (BrightnessControlHelper.hasAccess(appContext)) {
                    if (previewOnly) {
                        scheduleBrightnessWrite(clamped)
                    } else {
                        commitBrightnessImmediately(clamped)
                    }
                }
            }
        }
    }

    private fun hasAccess(mode: Mode): Boolean = when (mode) {
        Mode.VOLUME -> VolumeControlHelper.hasAccess(appContext)
        Mode.BRIGHTNESS -> BrightnessControlHelper.hasAccess(appContext)
    }

    private fun fractionFor(rawY: Float): Float {
        if (anchorRawY.isNaN()) return baselineFraction
        val span = appContext.resources.displayMetrics.heightPixels
            .coerceAtLeast(1) * DRAG_SPAN_SCREEN_FRACTION
        return (baselineFraction + (anchorRawY - rawY) / span).coerceIn(0f, 1f)
    }

    private fun applyFraction(mode: Mode, fraction: Float) {
        lastFraction = fraction.coerceIn(0f, 1f)
        when (mode) {
            Mode.VOLUME ->
                VolumeControlHelper.setFraction(
                    appContext,
                    VolumeControlHelper.Stream.MEDIA,
                    lastFraction,
                )
            Mode.BRIGHTNESS -> applyBrightness(lastFraction)
        }
    }

    private fun applyBrightness(fraction: Float) {
        if (!BrightnessControlHelper.hasAccess(appContext)) return
        scheduleBrightnessWrite(fraction)
    }

    private fun scheduleBrightnessWrite(fraction: Float) {
        if (!BrightnessControlHelper.hasAccess(appContext)) return
        pendingBrightnessFraction.set(fraction.coerceIn(0f, 1f))
        val now = SystemClock.uptimeMillis()
        if (now - lastBrightnessWriteUptimeMs < BRIGHTNESS_WRITE_INTERVAL_MS) {
            ensureBrightnessWorkerScheduled()
            return
        }
        val latest = pendingBrightnessFraction.getAndSet(null)
        if (latest == null) return
        ensureManualBrightness()
        writeSystemBrightness(latest)
        lastBrightnessWriteUptimeMs = SystemClock.uptimeMillis()
    }

    private fun commitBrightnessImmediately(fraction: Float) {
        if (!BrightnessControlHelper.hasAccess(appContext)) return
        pendingBrightnessFraction.set(null)
        lastCommittedBrightnessLevel = Int.MIN_VALUE
        ensureManualBrightness()
        writeSystemBrightness(fraction.coerceIn(0f, 1f))
        lastBrightnessWriteUptimeMs = SystemClock.uptimeMillis()
    }

    private fun ensureBrightnessWorkerScheduled() {
        if (brightnessWriteRunning.compareAndSet(false, true)) {
            brightnessWriteExecutor.execute(::drainBrightnessWrites)
        }
    }

    private fun drainBrightnessWrites() {
        try {
            while (true) {
                val fraction = pendingBrightnessFraction.getAndSet(null) ?: break
                ensureManualBrightness()
                writeSystemBrightness(fraction)
                lastBrightnessWriteUptimeMs = SystemClock.uptimeMillis()
                if (pendingBrightnessFraction.get() == null) break
            }
        } finally {
            brightnessWriteRunning.set(false)
            if (pendingBrightnessFraction.get() != null) {
                ensureBrightnessWorkerScheduled()
            }
        }
    }

    private fun ensureManualBrightness() {
        if (manualBrightnessEnsured) return
        if (!BrightnessControlHelper.readAutoBrightnessEnabled(appContext)) {
            manualBrightnessEnsured = true
            return
        }
        if (BrightnessControlHelper.toggleAutoBrightness(appContext) != null) {
            manualBrightnessEnsured = true
        }
    }

    private fun readBrightnessFraction(): Float =
        BrightnessControlHelper.readBrightnessFraction(appContext)

    private fun writeSystemBrightness(fraction: Float): Boolean {
        if (!BrightnessControlHelper.hasAccess(appContext)) return false
        val clamped = fraction.coerceIn(0f, 1f)
        val max = brightnessMax()
        val min = brightnessMin()
        val targetIntLevel = if (max <= min) {
            min
        } else {
            (min + (max - min) * clamped).roundToInt()
        }
        if (targetIntLevel == lastCommittedBrightnessLevel) return true

        var synced = false
        val canWriteSettings = PermissionHelper.canWriteSettings(appContext) ||
            SecureSettingsHelper.hasWriteSecureSettings(appContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canWriteSettings) {
            synced = runCatching {
                Settings.System.putFloat(
                    appContext.contentResolver,
                    "screen_brightness_float",
                    clamped,
                )
            }.getOrDefault(false)
        }
        if (canWriteSettings) {
            synced = runCatching {
                Settings.System.putInt(
                    appContext.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    targetIntLevel,
                )
            }.getOrDefault(false) || synced
        }

        if (!synced && TaskManagerUtil.hasPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                synced = TaskManagerUtil.runShellCommand(
                    "settings",
                    "put",
                    "system",
                    "screen_brightness_float",
                    clamped.toString(),
                ) || synced
            }
            synced = TaskManagerUtil.runShellCommand(
                "settings",
                "put",
                "system",
                "screen_brightness",
                targetIntLevel.toString(),
            ) || synced
        }
        if (synced) {
            lastCommittedBrightnessLevel = targetIntLevel
            Log.d(TAG, "system brightness set to $targetIntLevel (fraction=$clamped, max=$max)")
        }
        return synced
    }

    @SuppressLint("DiscouragedApi")
    private fun brightnessMax(): Int {
        val res = appContext.resources
        val id = res.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        val configured = if (id != 0) res.getInteger(id) else 0
        if (configured > 0) return configured

        val currentLevel = Settings.System.getInt(
            appContext.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            255,
        )
        return when {
            currentLevel > 4095 -> 65535
            currentLevel > 2047 -> 4095
            currentLevel > 255 -> 2047
            else -> 255
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun brightnessMin(): Int {
        val res = appContext.resources
        val id = res.getIdentifier("config_screenBrightnessSettingMinimum", "integer", "android")
        return if (id != 0) res.getInteger(id) else 0
    }

    private companion object {
        private const val TAG = "ContinuousAdjustController"
        private const val DRAG_SPAN_SCREEN_FRACTION = 0.5f
        private const val BRIGHTNESS_WRITE_INTERVAL_MS = 16L
    }
}
