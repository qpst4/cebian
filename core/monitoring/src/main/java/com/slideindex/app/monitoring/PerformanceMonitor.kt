package com.slideindex.app.monitoring

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

data class PerformanceStatsSnapshot(
    val fps: Int = 0,
    val jankFrames: Int = 0,
    val windowMs: Long = 0,
    val updatedAtUptimeMs: Long = 0,
)

/**
 * Tracks overlay rendering frame rate via [Choreographer].
 */
class FrameRateMonitor(
    private val tag: String = TAG,
    private val reportIntervalMs: Long = DEFAULT_REPORT_INTERVAL_MS,
) {
    private val running = AtomicBoolean(false)
    private val frameCount = AtomicInteger(0)
    private val lastReportUptimeMs = AtomicLong(0L)
    private var lastFrameUptimeMs = 0L
    private var jankFrames = 0

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running.get()) return

            val uptimeMs = SystemClock.uptimeMillis()
            if (lastFrameUptimeMs > 0L) {
                val delta = uptimeMs - lastFrameUptimeMs
                if (delta > JANK_THRESHOLD_MS) {
                    jankFrames++
                }
            }
            lastFrameUptimeMs = uptimeMs
            frameCount.incrementAndGet()

            val lastReport = lastReportUptimeMs.get()
            if (lastReport == 0L) {
                lastReportUptimeMs.set(uptimeMs)
            } else if (uptimeMs - lastReport >= reportIntervalMs) {
                publish(uptimeMs - lastReport)
                lastReportUptimeMs.set(uptimeMs)
            }

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        frameCount.set(0)
        jankFrames = 0
        lastFrameUptimeMs = 0L
        lastReportUptimeMs.set(SystemClock.uptimeMillis())
        Choreographer.getInstance().postFrameCallback(frameCallback)
        Log.d(tag, "Frame rate monitoring started")
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        Log.d(tag, "Frame rate monitoring stopped")
    }

    private fun publish(elapsedMs: Long) {
        val frames = frameCount.getAndSet(0)
        if (elapsedMs <= 0L || frames <= 0) return
        val fps = frames * 1000.0 / elapsedMs
        val jank = jankFrames
        jankFrames = 0
        val snapshot = PerformanceStatsSnapshot(
            fps = fps.roundToInt(),
            jankFrames = jank,
            windowMs = elapsedMs,
            updatedAtUptimeMs = SystemClock.uptimeMillis(),
        )
        PerformanceMonitor.instance.onFrameRateReport(snapshot)
        Log.i(
            tag,
            "Overlay FPS: ${snapshot.fps} (frames=$frames, jank=$jank, window=${elapsedMs}ms)",
        )
    }

    companion object {
        private const val TAG = "FrameRateMonitor"
        private const val DEFAULT_REPORT_INTERVAL_MS = 5_000L
        private const val JANK_THRESHOLD_MS = 32L
    }
}

/**
 * Detects slow main-thread messages by timing [Looper] dispatch.
 */
class MainThreadWatchdog(
    private val tag: String = TAG,
    private val slowThresholdMs: Long = DEFAULT_SLOW_THRESHOLD_MS,
    private val reportIntervalMs: Long = DEFAULT_REPORT_INTERVAL_MS,
) {
    private val running = AtomicBoolean(false)
    private val slowCount = AtomicInteger(0)
    private val dispatchCount = AtomicInteger(0)
    private var monitorStartUptimeMs = 0L
    private var dispatchStartUptimeMs = 0L

    fun start() {
        if (!running.compareAndSet(false, true)) return
        monitorStartUptimeMs = SystemClock.uptimeMillis()
        slowCount.set(0)
        dispatchCount.set(0)
        Looper.getMainLooper().setMessageLogging { message ->
            if (!running.get()) return@setMessageLogging
            if (message.startsWith(">>>>> Dispatching to ")) {
                dispatchStartUptimeMs = SystemClock.uptimeMillis()
            } else if (message.startsWith("<<<<< Finished to ")) {
                val duration = SystemClock.uptimeMillis() - dispatchStartUptimeMs
                dispatchCount.incrementAndGet()
                if (duration >= slowThresholdMs) {
                    slowCount.incrementAndGet()
                    Log.w(tag, "Slow main-thread dispatch: ${duration}ms $message")
                }
                maybeReport()
            }
        }
        Log.d(tag, "Main thread watchdog started (threshold=${slowThresholdMs}ms)")
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        Looper.getMainLooper().setMessageLogging(null)
        Log.d(tag, "Main thread watchdog stopped")
    }

    private fun maybeReport() {
        val elapsed = SystemClock.uptimeMillis() - monitorStartUptimeMs
        if (elapsed < reportIntervalMs) return
        val slow = slowCount.getAndSet(0)
        val total = dispatchCount.getAndSet(0)
        monitorStartUptimeMs = SystemClock.uptimeMillis()
        if (total == 0) return
        Log.i(tag, "Main thread summary: dispatches=$total, slow=$slow, window=${elapsed}ms")
    }

    companion object {
        private const val TAG = "MainThreadWatchdog"
        private const val DEFAULT_SLOW_THRESHOLD_MS = 16L
        private const val DEFAULT_REPORT_INTERVAL_MS = 5_000L
    }
}

/**
 * Coordinates overlay performance monitors for debug builds and verbose logging.
 */
class PerformanceMonitor private constructor() {
    val frameRateMonitor = FrameRateMonitor()
    val mainThreadWatchdog = MainThreadWatchdog()

    private val overlayRefCount = AtomicInteger(0)

    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    private var userPreferenceEnabled: Boolean = false

    @Volatile
    var latestStats: PerformanceStatsSnapshot? = null
        private set

    fun onFrameRateReport(snapshot: PerformanceStatsSnapshot) {
        latestStats = snapshot
    }

    fun setUserPreference(enabled: Boolean) {
        userPreferenceEnabled = enabled
        syncEnabled()
    }

    fun acquireOverlay() {
        overlayRefCount.incrementAndGet()
        syncEnabled()
    }

    fun releaseOverlay() {
        overlayRefCount.updateAndGet { (it - 1).coerceAtLeast(0) }
        syncEnabled()
    }

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        if (enabled) {
            mainThreadWatchdog.start()
            frameRateMonitor.start()
        } else {
            frameRateMonitor.stop()
            mainThreadWatchdog.stop()
        }
    }

    private fun syncEnabled() {
        setEnabled(userPreferenceEnabled && overlayRefCount.get() > 0)
    }

    internal fun resetForTesting() {
        overlayRefCount.set(0)
        userPreferenceEnabled = false
        latestStats = null
        setEnabled(false)
    }

    companion object {
        val instance: PerformanceMonitor = PerformanceMonitor()

        fun setUserPreference(enabled: Boolean) {
            instance.setUserPreference(enabled)
        }

        fun acquireOverlay() {
            instance.acquireOverlay()
        }

        fun releaseOverlay() {
            instance.releaseOverlay()
        }

        fun resetForTesting() {
            instance.resetForTesting()
        }

        fun setEnabled(enabled: Boolean) {
            instance.setEnabled(enabled)
        }
    }
}
