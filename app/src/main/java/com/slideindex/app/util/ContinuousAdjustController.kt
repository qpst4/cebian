package com.slideindex.app.util

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

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
    private var lastCommittedFraction = Float.NaN
    private val brightnessWriteExecutor = Executors.newSingleThreadExecutor()
    private val pendingBrightnessFraction = AtomicReference<Float?>(null)
    private val brightnessWriteRunning = AtomicBoolean(false)
    private var lastBrightnessWriteUptimeMs = 0L

    fun begin(mode: Mode, rawY: Float): Boolean {
        if (!hasAccess(mode)) return false
        if (activeMode != mode) {
            activeMode = mode
            anchorRawY = rawY
            lastCommittedFraction = Float.NaN
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
        lastCommittedFraction = Float.NaN
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
        Mode.BRIGHTNESS -> BrightnessControlHelper.readBrightnessFraction(appContext)
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
        writeSystemBrightness(latest)
        lastBrightnessWriteUptimeMs = SystemClock.uptimeMillis()
    }

    private var lastWriteWasDuringGesture = false

    private fun commitBrightnessImmediately(fraction: Float) {
        if (!BrightnessControlHelper.hasAccess(appContext)) return
        pendingBrightnessFraction.set(null)
        lastCommittedFraction = Float.NaN
        lastWriteWasDuringGesture = false
        writeSystemBrightness(fraction.coerceIn(0f, 1f), commitAutoAdj = true)
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

    private fun writeSystemBrightness(fraction: Float, commitAutoAdj: Boolean = false) {
        if (!BrightnessControlHelper.hasAccess(appContext)) return
        val clamped = fraction.coerceIn(0f, 1f)
        if (!lastCommittedFraction.isNaN() &&
            abs(lastCommittedFraction - clamped) < FRACTION_DEDUP_EPSILON &&
            lastWriteWasDuringGesture == !commitAutoAdj
        ) {
            return
        }
        val duringGesture = !commitAutoAdj
        val synced = BrightnessControlHelper.writeBrightnessFraction(
            appContext,
            clamped,
            duringGesture = duringGesture,
            commitAutoAdj = commitAutoAdj,
        )
        if (synced) {
            lastCommittedFraction = clamped
            lastWriteWasDuringGesture = duringGesture
        }
    }

    private companion object {
        private const val DRAG_SPAN_SCREEN_FRACTION = 0.5f
        private const val BRIGHTNESS_WRITE_INTERVAL_MS = 32L
        private const val FRACTION_DEDUP_EPSILON = 0.004f
    }
}
