package com.slideindex.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlin.math.roundToInt

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

    fun begin(mode: Mode, rawY: Float): Boolean {
        if (!hasAccess(mode)) return false
        if (activeMode != mode) {
            activeMode = mode
            anchorRawY = rawY
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
        if (mode == Mode.BRIGHTNESS && !fraction.isNaN()) {
            clearBrightnessPreview()
            val commitFraction = fraction
            Thread {
                ensureManualBrightness()
                writeSystemBrightness(commitFraction)
            }.start()
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
                if (overlayBrightness != null) {
                    overlayBrightness.apply(clamped)
                    if (!previewOnly) {
                        Thread {
                            ensureManualBrightness()
                            writeSystemBrightness(clamped)
                        }.start()
                    }
                } else {
                    writeSystemBrightness(clamped)
                }
            }
        }
    }

    private fun hasAccess(mode: Mode): Boolean = when (mode) {
        Mode.VOLUME -> VolumeControlHelper.hasAccess(appContext)
        Mode.BRIGHTNESS ->
            BrightnessControlHelper.hasAccess(appContext) || overlayBrightness != null
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
        // Live drag: overlay preview only — system/Shell writes block the main thread and cause ANR.
        if (overlayBrightness != null) {
            overlayBrightness.apply(fraction)
            return
        }
        writeSystemBrightness(fraction)
    }

    private fun ensureManualBrightness() {
        if (!BrightnessControlHelper.readAutoBrightnessEnabled(appContext)) return
        BrightnessControlHelper.toggleAutoBrightness(appContext)
    }

    private fun readBrightnessFraction(): Float {
        val max = brightnessMax()
        val min = brightnessMin()
        if (max <= min) return 0f
        val level = Settings.System.getInt(
            appContext.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            max,
        )
        return ((level - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    }

    private fun writeSystemBrightness(fraction: Float): Boolean {
        if (!BrightnessControlHelper.hasAccess(appContext)) return false
        val max = brightnessMax()
        val min = brightnessMin()
        val level = (min + (max - min) * fraction.coerceIn(0f, 1f)).roundToInt()
        if (level == lastCommittedBrightnessLevel) return true
        var synced = false
        if (TaskManagerUtil.hasPermission()) {
            synced = TaskManagerUtil.runShellCommand(
                "settings",
                "put",
                "system",
                "screen_brightness",
                level.toString(),
            )
        }
        if (PermissionHelper.canWriteSettings(appContext)) {
            synced = Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                level,
            ) || synced
        }
        if (synced) {
            lastCommittedBrightnessLevel = level
            Log.d(TAG, "system brightness set to $level (max=$max, fraction=$fraction)")
        }
        return synced
    }

    @SuppressLint("DiscouragedApi") // Framework brightness max via android.R integer
    private fun brightnessMax(): Int {
        val res = appContext.resources
        val id = res.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        val configured = if (id != 0) res.getInteger(id) else 0
        return configured.takeIf { it > 0 } ?: 255
    }

    @SuppressLint("DiscouragedApi") // Framework brightness min via android.R integer
    private fun brightnessMin(): Int {
        val res = appContext.resources
        val id = res.getIdentifier("config_screenBrightnessSettingMinimum", "integer", "android")
        return if (id != 0) res.getInteger(id) else 0
    }

    private companion object {
        private const val TAG = "ContinuousAdjustController"
        private const val DRAG_SPAN_SCREEN_FRACTION = 0.5f
    }
}
