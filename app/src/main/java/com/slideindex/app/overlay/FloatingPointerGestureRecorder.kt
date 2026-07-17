package com.slideindex.app.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.service.SlideIndexAccessibilityGestureInjector
import com.slideindex.app.util.InputTapUtil
import kotlin.math.hypot
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Quick Cursor-style gesture recorder (QC `o21` / `j60` / `ko` / `b60`).
 *
 * One shared [points] list is used for recording samples (`j60`).
 * Trail retreat is driven by a frozen snapshot + replay wall-clock in the session.
 * Replay injection uses a private copy so OEM fallback paths cannot break the trail UI.
 */
internal class FloatingPointerGestureRecorder(
    private val service: AccessibilityService,
    startX: Float,
    startY: Float,
    private val points: CopyOnWriteArrayList<GestureRecorderTrailPoint>,
    private val onPointsChanged: () -> Unit,
    private val onReplayPrepare: (replayDurationMs: Long) -> Unit,
    private val onFinished: () -> Unit,
) {
    private val executor = ScheduledThreadPoolExecutor(1)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scheduledFuture: ScheduledFuture<*>? = null
    private var currentX: Int = startX.toInt()
    private var currentY: Int = startY.toInt()
    private var lastSampleTimeMs: Long = System.currentTimeMillis()
    private var running = true

    init {
        // QC j60 ctor: two seed points, then schedule sampler.
        commitPoint(GestureRecorderTrailPoint(currentX, currentY, 0L))
        commitPoint(GestureRecorderTrailPoint(currentX, currentY, 1L))
        lastSampleTimeMs = System.currentTimeMillis()
        val sampler = Runnable {
            if (!running) return@Runnable
            mainHandler.post {
                if (!running) return@post
                val now = System.currentTimeMillis()
                val duration = (now - lastSampleTimeMs).coerceAtLeast(1L)
                // QC c case 26: stamp duration on head, then append a copy as the new head.
                val head = points.lastOrNull() ?: return@post
                points[points.lastIndex] = head.copy(durationMs = duration)
                commitPoint(GestureRecorderTrailPoint(currentX, currentY, duration))
                lastSampleTimeMs = now
            }
        }
        scheduledFuture = executor.scheduleWithFixedDelay(
            sampler,
            SAMPLE_INTERVAL_MS,
            SAMPLE_INTERVAL_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    /** QC `o21.e`: mutate the live head point to the current pointer position. */
    fun updatePosition(x: Float, y: Float) {
        if (!running) return
        val xInt = x.toInt()
        val yInt = y.toInt()
        currentX = xInt
        currentY = yInt
        val lastIndex = points.lastIndex
        if (lastIndex >= 0) {
            val last = points[lastIndex]
            if (last.x != xInt || last.y != yInt) {
                points[lastIndex] = last.copy(x = xInt, y = yInt)
                onPointsChanged()
            }
        }
    }

    /** QC `o21.h`: stop sampling, finalize head duration, then start `ko` replay. */
    fun finish() {
        if (!running) return
        running = false
        scheduledFuture?.cancel(false)
        executor.shutdownNow()

        val lastIndex = points.lastIndex
        if (lastIndex >= 0) {
            points[lastIndex] = points[lastIndex].copy(x = currentX, y = currentY)
        }
        val tailDuration = System.currentTimeMillis() - lastSampleTimeMs
        if (tailDuration == 0L) {
            // QC: if final duration is 0, drop the head sample.
            if (points.isNotEmpty()) {
                points.removeAt(points.lastIndex)
                onPointsChanged()
            }
        } else if (points.isNotEmpty()) {
            val last = points.last()
            points[points.lastIndex] = last.copy(durationMs = tailDuration)
            onPointsChanged()
        }

        Log.i(TAG, "finish: captured ${points.size} points")
        if (points.size < 2) {
            onFinished()
            return
        }
        val first = points.first()
        val last = points.last()
        val travelPx = hypot(
            (last.x - first.x).toDouble(),
            (last.y - first.y).toDouble(),
        )
        val totalDurationMs = totalDuration(points)
        Log.i(
            TAG,
            "finish: replay from (${first.x},${first.y}) to (${last.x},${last.y}) " +
                "travel=${travelPx.toInt()}px duration=${totalDurationMs}ms",
        )
        val canReplay = travelPx >= MIN_REPLAY_TRAVEL_PX ||
            totalDurationMs >= MIN_HOLD_REPLAY_MS
        if (!canReplay) {
            Log.w(TAG, "finish: gesture too short, skipping replay")
            onFinished()
            return
        }
        onReplayPrepare(totalDurationMs)
        // QC starts `ko` immediately after passthrough; we keep a short delay so the finger
        // is fully up before injection (OEM skins cancel overlapping touches).
        mainHandler.postDelayed({
            val replayPoints = CopyOnWriteArrayList(points.map { it.copy() })
            when {
                travelPx < STATIONARY_TRAVEL_PX -> replayHold(replayPoints, totalDurationMs) {
                    mainHandler.post { onFinished() }
                }
                else -> {
                    GesturePlaybackDispatcher(
                        service = service,
                        points = replayPoints,
                        onFinished = { mainHandler.post { onFinished() } },
                    )
                }
            }
        }, REPLAY_DELAY_MS)
    }

    private fun commitPoint(point: GestureRecorderTrailPoint) {
        points.add(point)
        if (points.size > MAX_POINTS) {
            points.removeAt(0)
            if (points.isNotEmpty()) {
                points[0] = points[0].copy(durationMs = 0L)
            }
        }
        onPointsChanged()
    }

    private fun replayHold(
        points: CopyOnWriteArrayList<GestureRecorderTrailPoint>,
        totalDurationMs: Long,
        onComplete: () -> Unit,
    ) {
        val first = points.first()
        val durationMs = totalDurationMs.coerceIn(
            MIN_HOLD_REPLAY_MS,
            SlideIndexAccessibilityGestureInjector.MAX_HOLD_DURATION_MS,
        )
        Log.i(TAG, "replayHold at (${first.x},${first.y}) duration=${durationMs}ms")
        InputTapUtil.dispatchPointerHoldAsync(
            rawX = first.x.toFloat(),
            rawY = first.y.toFloat(),
            durationMs = durationMs,
            onFinished = { ok ->
                Log.i(TAG, "replayHold finished ok=$ok")
                onComplete()
            },
        )
    }

    private fun replaySinglePath(
        points: CopyOnWriteArrayList<GestureRecorderTrailPoint>,
        onComplete: () -> Unit,
    ) {
        val built = buildReplayPath(points) ?: run {
            Log.w(TAG, "replaySinglePath: empty path")
            onComplete()
            return
        }
        val (path, durationMs) = built
        val first = points.first()
        Log.i(TAG, "replaySinglePath duration=${durationMs}ms points=${points.size}")
        InputTapUtil.dispatchPointerSwipePathAsync(
            startX = first.x.toFloat(),
            startY = first.y.toFloat(),
            path = path,
            durationMs = durationMs,
            maxDurationMs = SlideIndexAccessibilityGestureInjector.MAX_RECORDED_GESTURE_DURATION_MS,
            onFinished = { ok ->
                Log.i(TAG, "replaySinglePath finished ok=$ok")
                onComplete()
            },
        )
    }

    /**
     * QC `ko`: chained continueStroke over a replay-only copy of the recorded points.
     */
    private class GesturePlaybackDispatcher(
        private val service: AccessibilityService,
        private val points: CopyOnWriteArrayList<GestureRecorderTrailPoint>,
        private val onFinished: () -> Unit,
    ) : AccessibilityService.GestureResultCallback() {

        private val mainHandler = Handler(Looper.getMainLooper())
        private var stroke: GestureDescription.StrokeDescription? = null
        private var pendingOffsetMs: Long = 0L
        private var usedFallback = false
        /** QC `ko.e`: whether another segment follows the one being dispatched. */
        private var segmentWillContinue = false

        init {
            dispatchNext()
        }

        private fun dispatchNext() {
            if (points.size < 2) {
                finishPlayback()
                return
            }
            val first = points[0]
            val second = points[1]
            segmentWillContinue = 1 < points.size - 1
            val samePosition = first.x == second.x && first.y == second.y
            val firstDurationZero = first.durationMs == 0L

            if ((!firstDurationZero && samePosition && segmentWillContinue) || second.durationMs == 0L) {
                pendingOffsetMs += second.durationMs
                mainHandler.post { advanceAfterSegment() }
                return
            }

            val endX = second.x
            val endY = if (samePosition) second.y + 1 else second.y

            val path = Path().apply {
                moveTo(first.x.toFloat(), first.y.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }

            val segmentDuration = second.durationMs
                .coerceIn(1L, SlideIndexAccessibilityGestureInjector.MAX_RECORDED_GESTURE_DURATION_MS)
            val currentStroke = stroke
            val nextStroke = if (currentStroke == null) {
                GestureDescription.StrokeDescription(
                    path,
                    pendingOffsetMs,
                    segmentDuration,
                    segmentWillContinue,
                )
            } else {
                currentStroke.continueStroke(
                    path,
                    pendingOffsetMs,
                    segmentDuration,
                    segmentWillContinue,
                )
            }
            stroke = nextStroke
            pendingOffsetMs = 0L

            val builder = GestureDescription.Builder().addStroke(nextStroke)
            val dispatched = try {
                service.dispatchGesture(builder.build(), this, mainHandler)
            } catch (e: Exception) {
                Log.e(TAG, "dispatchGesture failed", e)
                false
            }
            if (!dispatched) {
                Log.w(TAG, "dispatchGesture rejected, falling back to single path")
                replayFallback()
            }
        }

        private fun replayFallback() {
            if (usedFallback) {
                finishPlayback()
                return
            }
            usedFallback = true
            stroke = null
            val built = buildReplayPath(points) ?: run {
                finishPlayback()
                return
            }
            val (path, durationMs) = built
            val first = points.first()
            InputTapUtil.dispatchPointerSwipePathAsync(
                startX = first.x.toFloat(),
                startY = first.y.toFloat(),
                path = path,
                durationMs = durationMs,
                maxDurationMs = SlideIndexAccessibilityGestureInjector.MAX_RECORDED_GESTURE_DURATION_MS,
                onFinished = { finishPlayback() },
            )
        }

        private fun advanceAfterSegment() {
            if (!segmentWillContinue) {
                finishPlayback()
                return
            }
            if (points.isNotEmpty()) {
                points.removeAt(0)
            }
            dispatchNext()
        }

        private fun finishPlayback() {
            mainHandler.post { onFinished() }
        }

        override fun onCancelled(gestureDescription: GestureDescription?) {
            Log.w(TAG, "chained replay cancelled")
            replayFallback()
        }

        override fun onCompleted(gestureDescription: GestureDescription?) {
            advanceAfterSegment()
        }
    }

    companion object {
        private const val TAG = "FpGestureRecorder"
        const val REPLAY_START_DELAY_MS = 120L
        private const val REPLAY_DELAY_MS = REPLAY_START_DELAY_MS
        private const val MIN_REPLAY_TRAVEL_PX = 24.0
        private const val STATIONARY_TRAVEL_PX = 3.0
        private const val MIN_HOLD_REPLAY_MS = 200L
        private val SAMPLE_INTERVAL_MS = 25L
        private val MAX_POINTS = 200

        private fun totalDuration(points: CopyOnWriteArrayList<GestureRecorderTrailPoint>): Long =
            points.sumOf { point -> if (point.durationMs > 0L) point.durationMs else 0L }

        private fun buildReplayPath(
            points: CopyOnWriteArrayList<GestureRecorderTrailPoint>,
        ): Pair<Path, Long>? {
            if (points.size < 2) return null
            val path = Path()
            var totalDuration = 0L
            var hasSegment = false
            val first = points.first()
            var lastX = first.x
            var lastY = first.y
            path.moveTo(first.x.toFloat(), first.y.toFloat())
            for (index in 1 until points.size) {
                val point = points[index]
                if (point.durationMs <= 0L) continue
                val endY = if (point.x == lastX && point.y == lastY) {
                    point.y + 1f
                } else {
                    point.y.toFloat()
                }
                path.lineTo(point.x.toFloat(), endY)
                lastX = point.x
                lastY = point.y
                totalDuration += point.durationMs
                hasSegment = true
            }
            if (!hasSegment) return null
            return path to totalDuration.coerceIn(
                MIN_HOLD_REPLAY_MS,
                SlideIndexAccessibilityGestureInjector.MAX_RECORDED_GESTURE_DURATION_MS,
            )
        }
    }
}
