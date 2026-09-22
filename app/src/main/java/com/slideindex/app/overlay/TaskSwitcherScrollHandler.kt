package com.slideindex.app.overlay

import android.view.Choreographer
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.OverScroller
import com.slideindex.app.overlay.layout.TaskSwitcherPanelLayout
import kotlin.math.abs
import kotlin.math.roundToInt

internal class TaskSwitcherScrollHandler(
    private val touch: TaskSwitcherTouchHandler
) {
    private val ctrl get() = touch.ctrl
    private val host get() = touch.host
    private val overscrollMotion get() = ctrl.taskSwitcherOverscrollMotion
    private val viewConfiguration by lazy { ViewConfiguration.get(host.context) }
    private val flingScroller = OverScroller(host.context)
    private var velocityTracker: VelocityTracker? = null
    private var flingActive = false
    private var flingAxisVelocity = 0f
    private var flingStartAxisVelocity = 0f
    private var flingStartOffset = 0f
    private var flingPeakTopAxisVelocity = 0f
    private var flingPeakBottomAxisVelocity = 0f

    private val flingFrameCallback = Choreographer.FrameCallback { onFlingFrame() }
    private val minFlingEdgeBounceVelocity: Float
        get() = viewConfiguration.scaledMinimumFlingVelocity * FLING_EDGE_BOUNCE_VELOCITY_FRACTION
    private val flingEdgeApproachPx: Float
        get() = host.dp(FLING_EDGE_APPROACH_DP)

    val isFlinging: Boolean
        get() = flingActive

    val isScrollMotionActive: Boolean
        get() = ctrl.taskSwitcherScrollDragging || flingActive || overscrollMotion.isRunning

    fun beginScrollDrag(localY: Float) {
        cancelFling()
        recycleVelocityTracker()
        velocityTracker = VelocityTracker.obtain()
        ctrl.taskSwitcherScrollDragStartY = localY
        ctrl.taskSwitcherScrollDragStartOffset = ctrl.taskSwitcherScrollOffset
        ctrl.taskSwitcherScrollDragging = false
    }

    fun trackVelocity(event: MotionEvent) {
        velocityTracker?.addMovement(event)
    }

    fun handleScrollMove(touchX: Float, localY: Float): Boolean {
        val layout = ctrl.taskSwitcherLayout ?: return false
        if (!layout.listRect.contains(touchX, localY)) return false
        val canScroll = layout.maxScrollOffset > 0f
        val canOverscroll = ctrl.taskSwitcherOverscrollEnabled()
        if (!canScroll && !canOverscroll) return false
        val dy = localY - ctrl.taskSwitcherScrollDragStartY
        if (!ctrl.taskSwitcherScrollDragging &&
            abs(dy) <= host.dp(TaskSwitcherOverlayController.TASK_SWITCHER_SCROLL_SLOP_DP)
        ) {
            return false
        }
        if (!ctrl.taskSwitcherScrollDragging) {
            ctrl.taskSwitcherScrollDragging = true
            cancelScrollMotionAnimations()
            touch.longPressHandler.cancelCloseLongPress()
            touch.longPressHandler.cancelRowLongPress()
            ctrl.taskSwitcherClosePressIndex = -1
            ctrl.taskSwitcherRowPressIndex = -1
            touch.clearTaskSwitcherPickHighlights()
        }
        val rawOffset = ctrl.taskSwitcherScrollDragStartOffset + ctrl.taskSwitcherScrollDragStartY - localY
        if (!canScroll) {
            ctrl.taskSwitcherScrollOffset = 0f
            ctrl.taskSwitcherOverscrollOffset = -rubberBand(rawOffset)
            host.invalidate()
            return true
        }
        when {
            rawOffset < 0f -> {
                ctrl.taskSwitcherScrollOffset = 0f
                ctrl.taskSwitcherOverscrollOffset = if (canOverscroll) {
                    -rubberBand(rawOffset)
                } else {
                    0f
                }
            }
            rawOffset > layout.maxScrollOffset -> {
                ctrl.taskSwitcherScrollOffset = layout.maxScrollOffset
                val excess = rawOffset - layout.maxScrollOffset
                ctrl.taskSwitcherOverscrollOffset = if (canOverscroll) {
                    -rubberBand(excess)
                } else {
                    0f
                }
            }
            else -> {
                ctrl.taskSwitcherScrollOffset = rawOffset
                ctrl.taskSwitcherOverscrollOffset = 0f
            }
        }
        ctrl.taskSwitcherLayout = null
        markGestureScrolledIfNeeded()
        host.invalidate()
        return true
    }

    fun finishScrollDrag(event: MotionEvent): Boolean {
        trackVelocity(event)
        val wasDragging = ctrl.taskSwitcherScrollDragging
        ctrl.taskSwitcherScrollDragging = false
        if (!wasDragging) {
            recycleVelocityTracker()
            return false
        }
        if (!ctrl.taskSwitcherOverscrollEnabled()) {
            recycleVelocityTracker()
            return true
        }
        if (abs(ctrl.taskSwitcherOverscrollOffset) >= 0.5f) {
            releaseOverscroll()
            recycleVelocityTracker()
            return true
        }
        val layout = ctrl.taskSwitcherLayout ?: ctrl.computeTaskSwitcherLayout().also { ctrl.taskSwitcherLayout = it }
        val tracker = velocityTracker
        if (tracker != null && layout.maxScrollOffset > 0f) {
            tracker.computeCurrentVelocity(
                1000,
                viewConfiguration.scaledMaximumFlingVelocity.toFloat()
            )
            val velocityY = tracker.yVelocity
            recycleVelocityTracker()
            if (abs(velocityY) >= viewConfiguration.scaledMinimumFlingVelocity) {
                startFling(velocityY, layout.maxScrollOffset)
                return true
            }
        } else {
            recycleVelocityTracker()
        }
        releaseOverscroll()
        return true
    }

    fun applyEdgeAutoScroll(layout: TaskSwitcherPanelLayout, localY: Float): Boolean {
        val edge = host.dp(20f)
        val step = host.dp(10f)
        when {
            localY < layout.listRect.top + edge && ctrl.taskSwitcherScrollOffset > 0f -> {
                val next = (ctrl.taskSwitcherScrollOffset - step).coerceAtLeast(0f)
                if (next == ctrl.taskSwitcherScrollOffset) return false
                ctrl.taskSwitcherScrollOffset = next
                ctrl.taskSwitcherLayout = null
                host.invalidate()
                return true
            }
            localY > layout.listRect.bottom - edge &&
                ctrl.taskSwitcherScrollOffset < layout.maxScrollOffset -> {
                val next = (ctrl.taskSwitcherScrollOffset + step).coerceAtMost(layout.maxScrollOffset)
                if (next == ctrl.taskSwitcherScrollOffset) return false
                ctrl.taskSwitcherScrollOffset = next
                ctrl.taskSwitcherLayout = null
                host.invalidate()
                return true
            }
            else -> return false
        }
    }

    fun scrollToFollowFinger(localY: Float) {
        if (ctrl.recentApps.isEmpty()) return
        cancelFling()
        val layout = ctrl.taskSwitcherLayout ?: ctrl.computeTaskSwitcherLayout().also { ctrl.taskSwitcherLayout = it }
        val rowHeight = host.dp(42f)
        val fingerInList = (localY - layout.listRect.top).coerceIn(0f, layout.listRect.height())
        val contentY = fingerInList + layout.scrollOffset
        val index = (contentY / rowHeight).toInt().coerceIn(0, ctrl.recentApps.lastIndex)
        val desiredOffset = index * rowHeight + rowHeight / 2f - fingerInList
        val clamped = desiredOffset.coerceIn(0f, layout.maxScrollOffset)
        if (abs(clamped - ctrl.taskSwitcherScrollOffset) < 0.5f) return
        ctrl.taskSwitcherScrollOffset = clamped
        ctrl.taskSwitcherLayout = null
        markGestureScrolledIfNeeded()
    }

    fun markGestureScrolledIfNeeded() {
        if (!host.gestureSession().taskSwitcherContinuousPickActive()) {
            ctrl.taskSwitcherGestureScrolled = true
        }
    }

    fun cancelScrollMotion() {
        cancelFling()
        cancelOverscrollAnimation()
    }

    fun cancelOverscrollAnimation() {
        overscrollMotion.cancel()
    }

    private fun startFling(velocityY: Float, maxOffset: Float) {
        cancelFling()
        ctrl.taskSwitcherOverscrollOffset = 0f
        val startOffset = ctrl.taskSwitcherScrollOffset.roundToInt()
        val maxOffsetPx = maxOffset.roundToInt()
        val axisVelocity = mapTrackerVelocityToFling(velocityY).toFloat()
        flingAxisVelocity = axisVelocity
        flingStartAxisVelocity = axisVelocity
        flingStartOffset = ctrl.taskSwitcherScrollOffset
        flingPeakTopAxisVelocity = 0f
        flingPeakBottomAxisVelocity = 0f
        flingScroller.fling(
            0,
            startOffset,
            0,
            axisVelocity.roundToInt(),
            0,
            0,
            0,
            maxOffsetPx
        )
        flingActive = true
        markGestureScrolledIfNeeded()
        Choreographer.getInstance().postFrameCallback(flingFrameCallback)
    }

    private fun onFlingFrame() {
        if (!flingActive) return
        if (flingScroller.computeScrollOffset()) {
            val layout = ctrl.taskSwitcherLayout ?: ctrl.computeTaskSwitcherLayout().also {
                ctrl.taskSwitcherLayout = it
            }
            val maxOffset = layout.maxScrollOffset
            val nextOffset = flingScroller.currY.toFloat().coerceIn(0f, maxOffset)
            val axisVelocity = flingScroller.currVelocity
            flingAxisVelocity = axisVelocity
            trackFlingEdgeApproachVelocity(nextOffset, maxOffset, axisVelocity)
            ctrl.taskSwitcherScrollOffset = nextOffset
            ctrl.taskSwitcherLayout = null
            if (ctrl.taskSwitcherOverscrollEnabled() &&
                tryConsumeFlingEdgeBounce(nextOffset, maxOffset, axisVelocity, fromTerminalFrame = false)
            ) {
                return
            }
            host.invalidate()
            Choreographer.getInstance().postFrameCallback(flingFrameCallback)
            return
        }
        finishFling()
    }

    private fun trackFlingEdgeApproachVelocity(
        offset: Float,
        maxOffset: Float,
        axisVelocity: Float
    ) {
        if (offset <= flingEdgeApproachPx && axisVelocity < flingPeakTopAxisVelocity) {
            flingPeakTopAxisVelocity = axisVelocity
        }
        if (maxOffset > 0f &&
            offset >= maxOffset - flingEdgeApproachPx &&
            axisVelocity > flingPeakBottomAxisVelocity
        ) {
            flingPeakBottomAxisVelocity = axisVelocity
        }
    }

    private fun finishFling() {
        val terminalVelocity = flingAxisVelocity
        val snapshot = flingBounceSnapshot()
        stopFlingAnimation()
        if (ctrl.taskSwitcherOverscrollEnabled()) {
            val layout = ctrl.taskSwitcherLayout ?: ctrl.computeTaskSwitcherLayout().also {
                ctrl.taskSwitcherLayout = it
            }
            tryConsumeFlingEdgeBounce(
                offset = ctrl.taskSwitcherScrollOffset,
                maxOffset = layout.maxScrollOffset,
                axisVelocity = terminalVelocity,
                fromTerminalFrame = true,
                snapshot = snapshot
            )
        }
        ctrl.taskSwitcherLayout = null
        host.invalidate()
    }

    private fun tryConsumeFlingEdgeBounce(
        offset: Float,
        maxOffset: Float,
        axisVelocity: Float,
        fromTerminalFrame: Boolean,
        snapshot: FlingBounceSnapshot = flingBounceSnapshot()
    ): Boolean {
        val bounceVelocity = resolveFlingEdgeBounceVelocity(
            offset = offset,
            maxOffset = maxOffset,
            terminalVelocity = axisVelocity,
            startOffset = snapshot.startOffset,
            startVelocity = snapshot.startVelocity,
            peakTopVelocity = snapshot.peakTopVelocity,
            peakBottomVelocity = snapshot.peakBottomVelocity,
            minVelocity = minFlingEdgeBounceVelocity,
            fromTerminalFrame = fromTerminalFrame
        )
        if (bounceVelocity == 0f) return false
        stopFlingAnimation()
        ctrl.taskSwitcherOverscrollOffset = overscrollFromFlingVelocity(
            axisVelocity = bounceVelocity,
            resistance = TaskSwitcherOverlayController.TASK_SWITCHER_OVERSCROLL_RESISTANCE,
            maxOverscrollPx = host.dp(TaskSwitcherOverlayController.TASK_SWITCHER_OVERSCROLL_MAX_DP)
        )
        releaseOverscroll()
        host.invalidate()
        return true
    }

    private fun flingBounceSnapshot() = FlingBounceSnapshot(
        startOffset = flingStartOffset,
        startVelocity = flingStartAxisVelocity,
        peakTopVelocity = flingPeakTopAxisVelocity,
        peakBottomVelocity = flingPeakBottomAxisVelocity
    )

    private data class FlingBounceSnapshot(
        val startOffset: Float,
        val startVelocity: Float,
        val peakTopVelocity: Float,
        val peakBottomVelocity: Float
    )

    private fun stopFlingAnimation() {
        flingActive = false
        flingAxisVelocity = 0f
        flingStartAxisVelocity = 0f
        flingStartOffset = 0f
        flingPeakTopAxisVelocity = 0f
        flingPeakBottomAxisVelocity = 0f
        if (!flingScroller.isFinished) {
            flingScroller.abortAnimation()
        }
        Choreographer.getInstance().removeFrameCallback(flingFrameCallback)
    }

    private fun cancelFling() {
        if (!flingActive && flingScroller.isFinished) return
        stopFlingAnimation()
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun rubberBand(rawExcess: Float): Float {
        val sign = if (rawExcess >= 0f) 1f else -1f
        val resisted = abs(rawExcess) * TaskSwitcherOverlayController.TASK_SWITCHER_OVERSCROLL_RESISTANCE
        return sign * resisted.coerceAtMost(host.dp(TaskSwitcherOverlayController.TASK_SWITCHER_OVERSCROLL_MAX_DP))
    }

    private fun releaseOverscroll() {
        if (abs(ctrl.taskSwitcherOverscrollOffset) < 0.5f) {
            ctrl.taskSwitcherOverscrollOffset = 0f
            return
        }
        cancelOverscrollAnimation()
        val start = ctrl.taskSwitcherOverscrollOffset
        overscrollMotion.animateTo(
            start = start,
            target = 0f,
            epsilon = 0.5f,
            onValue = { value ->
                ctrl.taskSwitcherOverscrollOffset = value
                host.invalidate()
            },
            onComplete = {
                ctrl.taskSwitcherOverscrollOffset = 0f
                host.invalidate()
            }
        )
    }

    private fun cancelScrollMotionAnimations() {
        cancelFling()
        cancelOverscrollAnimation()
    }

    companion object {
        private const val FLING_EDGE_OVERSCROLL_VELOCITY_FACTOR = 0.018f
        private const val FLING_EDGE_BOUNCE_VELOCITY_FRACTION = 0.35f
        private const val FLING_EDGE_APPROACH_DP = 56f
        private const val FLING_EDGE_TERMINAL_VELOCITY_RETAIN = 0.22f
        private const val FLING_EDGE_MIN_OVERSCROLL_FRACTION = 0.14f

        internal fun mapTrackerVelocityToFling(velocityY: Float): Int =
            (-velocityY).roundToInt()

        internal fun resolveFlingEdgeBounceVelocity(
            offset: Float,
            maxOffset: Float,
            terminalVelocity: Float,
            startOffset: Float,
            startVelocity: Float,
            peakTopVelocity: Float,
            peakBottomVelocity: Float,
            minVelocity: Float,
            fromTerminalFrame: Boolean
        ): Float {
            val atTop = offset <= 0.5f
            val atBottom = maxOffset > 0f && offset >= maxOffset - 0.5f
            if (atTop) {
                val candidates = buildList {
                    if (terminalVelocity < -minVelocity) add(terminalVelocity)
                    if (peakTopVelocity < -minVelocity) add(peakTopVelocity)
                    if (startOffset <= 0.5f && startVelocity < -minVelocity) add(startVelocity)
                    if (fromTerminalFrame && startOffset > 0.5f && startVelocity < 0f) {
                        val retained = startVelocity * FLING_EDGE_TERMINAL_VELOCITY_RETAIN
                        if (retained < -minVelocity) add(retained)
                    }
                }
                return candidates.minOrNull() ?: 0f
            }
            if (atBottom) {
                val candidates = buildList {
                    if (terminalVelocity > minVelocity) add(terminalVelocity)
                    if (peakBottomVelocity > minVelocity) add(peakBottomVelocity)
                    if (startOffset >= maxOffset - 0.5f && startVelocity > minVelocity) add(startVelocity)
                    if (fromTerminalFrame && startOffset < maxOffset - 0.5f && startVelocity > 0f) {
                        val retained = startVelocity * FLING_EDGE_TERMINAL_VELOCITY_RETAIN
                        if (retained > minVelocity) add(retained)
                    }
                }
                return candidates.maxOrNull() ?: 0f
            }
            return 0f
        }

        internal fun overscrollFromFlingVelocity(
            axisVelocity: Float,
            resistance: Float,
            maxOverscrollPx: Float
        ): Float {
            val pseudoExcess = axisVelocity * FLING_EDGE_OVERSCROLL_VELOCITY_FACTOR
            val sign = if (pseudoExcess >= 0f) 1f else -1f
            val resisted = abs(pseudoExcess) * resistance
            val capped = resisted.coerceAtMost(maxOverscrollPx)
            if (capped < 0.5f) return 0f
            val raw = -sign * capped
            val minMagnitude = maxOverscrollPx * FLING_EDGE_MIN_OVERSCROLL_FRACTION
            return if (abs(raw) < minMagnitude) {
                if (raw >= 0f) minMagnitude else -minMagnitude
            } else {
                raw
            }
        }
    }
}
