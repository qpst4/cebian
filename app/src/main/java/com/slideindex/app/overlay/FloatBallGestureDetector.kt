package com.slideindex.app.overlay

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.floatball.FloatBallGestureType
import kotlin.math.abs
import kotlin.math.hypot

/**
 * FooView 风格：拖出后球体立即跟手；约 [PICK_GESTURE_LOCK_MS] 内松手走手势，
 * 超时后锁定取词/区域拾取，抬手不再触发滑动手势。
 * 首次滑出方向后锁定轴向，斜拖/改向不再切换手势提示与抬手判定。
 * 沿锁定轴往回滑则取消手势；再次往原方向滑出可重新触发提示与抬手判定。
 * 抬手判定记录锁定轴正向峰值位移；末尾微回弹在 [GESTURE_CANCEL_RETAIN_FRACTION] 容差内仍触发手势。
 * 单击/双击/长按不进入取词；slop 内球体微跟手，拖出 slop 后进入取词，[PICK_GESTURE_LOCK_MS] 从拖出时刻起算。
 * 黄框暂停（[lockPickFromPause]）与超时锁定等价：抬手仅取词，不触发滑动手势。
 */
internal class FloatBallGestureDetector(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    internal enum class LockedSwipeAxis {
        UP,
        DOWN,
        SIDE,
    }

    private companion object {
        /** 与 FV s4() 的 800ms 手势窗口一致：超时后仅取词。 */
        const val PICK_GESTURE_LOCK_MS = 800L
        const val LONG_PRESS_MS = 500L
        const val DOUBLE_TAP_MS = 300L
        const val DIRECTION_RATIO = 1.4f
        /** FV f11261s：40dp 基准。 */
        const val SWIPE_BASE_DP = 40f
        /**
         * 抬手时当前正向位移低于峰值的比例则视为取消；高于则仍触发（容忍末尾微回弹）。
         */
        const val GESTURE_CANCEL_RETAIN_FRACTION = 0.55f
    }

    private var density = 1f
    private var downSwipeShortPx = 60f
    private var sideSwipeShortPx = 60f
    private var upSwipeShortPx = 60f
    private var slopPx = 8f

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var pickActive = false
    /** 手指滑出 slop 后为 true，此后才跟手取词并启动手势窗口。 */
    private var pickDragStarted = false
    private var pickDragStartTime = 0L
    /** 超过手势窗口后为 true，抬手走取词而非手势。 */
    private var pickGestureLocked = false
    /** 黄框暂停后为 true，与 [pickGestureLocked] 一样锁定取词提交。 */
    private var pausePickLocked = false
    /** 首次滑出方向锁定后，仅沿该轴累计位移用于提示/抬手判定。 */
    private var lockedSwipeAxis: LockedSwipeAxis? = null
    /** 锁定轴上的正向符号：上=-1、下=+1、侧=sign(dx)。 */
    private var lockedAxisForwardSign = 0f
    /** 沿锁定轴正向滑出 slop 后为 true；往回滑取消，再次正向滑出可重新武装。 */
    private var gestureArmed = false
    /** 本次拖拽在锁定轴上达到的最大正向位移（px）。 */
    private var peakForwardProgressPx = 0f
    private var longPressFired = false
    /** 本次按下后是否曾滑出 touch slop；用于拖出再拖回时不误判为单击。 */
    private var movedBeyondSlop = false
    private var pendingSingleTap = false
    private var pendingSingleTapX = 0f
    private var pendingSingleTapY = 0f

    private var onPickPreviewStart: ((screenX: Float, screenY: Float) -> Unit)? = null
    private var onPickPreviewProgress: ((progress: Float) -> Unit)? = null
    private var onPickPreviewMove: ((touchDownX: Float, touchDownY: Float, fingerX: Float, fingerY: Float) -> Unit)? = null
    private var onPickPreviewCancel: (() -> Unit)? = null
    private var onPickStart: ((touchDownX: Float, touchDownY: Float, fingerX: Float, fingerY: Float) -> Unit)? = null
    private var onPickDrag: ((dx: Float, dy: Float) -> Unit)? = null
    private var onPickEnd: (() -> Unit)? = null
    private var onPickCancel: (() -> Unit)? = null
    private var onGesture: ((FloatBallGestureType, rawX: Float, rawY: Float) -> Unit)? = null
    private var onGestureHint: ((FloatBallGestureType?) -> Unit)? = null
    private var onLauncherCaptureMove: ((rawX: Float, rawY: Float) -> Unit)? = null
    private var onLauncherCaptureUp: ((rawX: Float, rawY: Float) -> Unit)? = null
    private var launcherCaptureMode = false

    private val pickGestureLockRunnable = Runnable {
        if (!pickDragStarted) return@Runnable
        pickGestureLocked = true
        onGestureHint?.invoke(null)
    }

    private val longPressRunnable = Runnable {
        if (pickDragStarted || isPickCommitLocked() || longPressFired) return@Runnable
        val dist = hypot(lastX - downX, lastY - downY)
        if (dist <= slopPx * 2f) {
            longPressFired = true
            pendingSingleTap = false
            onGesture?.invoke(FloatBallGestureType.LONG_PRESS, lastX, lastY)
        }
    }

    private val singleTapRunnable = Runnable {
        if (pendingSingleTap) {
            pendingSingleTap = false
            onGesture?.invoke(FloatBallGestureType.SINGLE_TAP, pendingSingleTapX, pendingSingleTapY)
        }
    }

    private var lastTapUpTime = 0L
    private var lastTapUpX = 0f
    private var lastTapUpY = 0f

    fun bind(
        settings: AppSettings,
        density: Float,
        onPickStart: (touchDownX: Float, touchDownY: Float, fingerX: Float, fingerY: Float) -> Unit,
        onPickDrag: (dx: Float, dy: Float) -> Unit,
        onPickEnd: () -> Unit,
        onPickCancel: () -> Unit,
        onGesture: (FloatBallGestureType, rawX: Float, rawY: Float) -> Unit,
        onGestureHint: (FloatBallGestureType?) -> Unit = {},
        onPickPreviewStart: (screenX: Float, screenY: Float) -> Unit = { _, _ -> },
        onPickPreviewProgress: (progress: Float) -> Unit = {},
        onPickPreviewMove: (touchDownX: Float, touchDownY: Float, fingerX: Float, fingerY: Float) -> Unit = { _, _, _, _ -> },
        onPickPreviewCancel: () -> Unit = {},
        onLauncherCaptureMove: (rawX: Float, rawY: Float) -> Unit = { _, _ -> },
        onLauncherCaptureUp: (rawX: Float, rawY: Float) -> Unit = { _, _ -> },
    ) {
        this.density = density
        downSwipeShortPx = swipeThresholdPx(settings.floatBallDownSwipeShortPercent, density)
        sideSwipeShortPx = swipeThresholdPx(settings.floatBallSideSwipeShortPercent, density)
        upSwipeShortPx = swipeThresholdPx(settings.floatBallUpSwipeShortPercent, density)
        slopPx = settings.floatBallPointerSlopDp.coerceIn(4f, 32f) * density
        this.onPickStart = onPickStart
        this.onPickDrag = onPickDrag
        this.onPickEnd = onPickEnd
        this.onPickCancel = onPickCancel
        this.onGesture = onGesture
        this.onGestureHint = onGestureHint
        this.onPickPreviewStart = onPickPreviewStart
        this.onPickPreviewProgress = onPickPreviewProgress
        this.onPickPreviewMove = onPickPreviewMove
        this.onPickPreviewCancel = onPickPreviewCancel
        this.onLauncherCaptureMove = onLauncherCaptureMove
        this.onLauncherCaptureUp = onLauncherCaptureUp
    }

    fun enterLauncherCaptureMode() {
        launcherCaptureMode = true
        pickActive = true
        longPressFired = false
        handler.removeCallbacks(longPressRunnable)
        pickDragStarted = false
        pickGestureLocked = false
        pausePickLocked = false
        onGestureHint?.invoke(null)
    }

    fun isLauncherCaptureMode(): Boolean = launcherCaptureMode

    fun cancelLauncherCaptureMode() {
        launcherCaptureMode = false
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelPendingSingleTap()
                resetTouchSession()
                downX = event.rawX
                downY = event.rawY
                lastX = downX
                lastY = downY
                downTime = SystemClock.uptimeMillis()
                pickActive = true
                onGestureHint?.invoke(null)
                onPickPreviewStart?.invoke(downX, downY)
                onPickPreviewMove?.invoke(downX, downY, downX, downY)
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pickActive) return true
                if (launcherCaptureMode) {
                    lastX = event.rawX
                    lastY = event.rawY
                    onLauncherCaptureMove?.invoke(event.rawX, event.rawY)
                    return true
                }
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                lastX = event.rawX
                lastY = event.rawY
                val distFromStart = hypot(event.rawX - downX, event.rawY - downY)
                if (!pickDragStarted) {
                    val progress = if (slopPx > 0f) (distFromStart / slopPx).coerceIn(0f, 1f) else 1f
                    onPickPreviewProgress?.invoke(progress)
                    if (distFromStart > 0f) {
                        onPickPreviewMove?.invoke(downX, downY, event.rawX, event.rawY)
                    }
                }
                if (distFromStart > slopPx) {
                    movedBeyondSlop = true
                    handler.removeCallbacks(longPressRunnable)
                    pendingSingleTap = false
                    if (!pickDragStarted) {
                        startPickDrag(event.rawX, event.rawY)
                    }
                }
                if (pickDragStarted) {
                    onPickDrag?.invoke(dx, dy)
                }
                val totalDx = event.rawX - downX
                val totalDy = event.rawY - downY
                lockSwipeAxisIfNeeded(totalDx, totalDy)
                updateGestureArmState(totalDx, totalDy, incrementalDx = dx, incrementalDy = dy)
                emitGestureHint(totalDx, totalDy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                onGestureHint?.invoke(null)
                cancelDeferredCallbacks()
                val totalDx = event.rawX - downX
                val totalDy = event.rawY - downY
                lockSwipeAxisIfNeeded(totalDx, totalDy)
                updateGestureArmState(totalDx, totalDy, incrementalDx = 0f, incrementalDy = 0f)
                val (dx, dy) = projectedDisplacement(totalDx, totalDy)
                val totalDist = hypot(dx, dy)
                val locked = pickDragStarted && isPickCommitLocked()
                when {
                    launcherCaptureMode -> {
                        onLauncherCaptureUp?.invoke(event.rawX, event.rawY)
                        resetTouchSession()
                        launcherCaptureMode = false
                    }
                    longPressFired -> finishGestureOnly()
                    locked -> finishPick()
                    shouldCommitSwipeGesture(dx, dy) -> {
                        classifySwipe(dx, dy)
                            ?.let { onGesture?.invoke(it, event.rawX, event.rawY) }
                        finishGestureOnly()
                    }
                    movedBeyondSlop || totalDist > slopPx -> finishGestureOnly()
                    else -> {
                        classifyTapRelease(event.rawX, event.rawY)
                        finishGestureOnly()
                    }
                }
                resetTouchSession()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                onGestureHint?.invoke(null)
                cancelDeferredCallbacks()
                cancelPendingSingleTap()
                if (launcherCaptureMode) {
                    onLauncherCaptureUp?.invoke(lastX, lastY)
                } else if (pickActive) {
                    pickActive = false
                    if (pickDragStarted) {
                        onPickCancel?.invoke()
                    } else {
                        onPickPreviewCancel?.invoke()
                    }
                }
                resetTouchSession()
                return true
            }
        }
        return false
    }

    fun cancel() {
        cancelDeferredCallbacks()
        cancelPendingSingleTap()
        if (pickActive) {
            pickActive = false
            if (pickDragStarted) {
                onPickCancel?.invoke()
            } else {
                onPickPreviewCancel?.invoke()
            }
        }
        resetTouchSession()
    }

    /** 黄框暂停：与超时锁定相同，抬手仅提交取词。 */
    fun lockPickFromPause() {
        if (!pickDragStarted) return
        pausePickLocked = true
        gestureArmed = false
        peakForwardProgressPx = 0f
        onGestureHint?.invoke(null)
    }

    /** 晃回暂停原点取消黄框时，若未到 [PICK_GESTURE_LOCK_MS] 则恢复手势判定。 */
    fun unlockPickFromPause() {
        if (!pickDragStarted) {
            pausePickLocked = false
            return
        }
        if (SystemClock.uptimeMillis() - pickDragStartTime < PICK_GESTURE_LOCK_MS) {
            pausePickLocked = false
        }
    }

    private fun isPickCommitLocked(): Boolean {
        return pickGestureLocked ||
            pausePickLocked ||
            (pickDragStarted &&
                SystemClock.uptimeMillis() - pickDragStartTime >= PICK_GESTURE_LOCK_MS)
    }

    private fun startPickDrag(fingerX: Float, fingerY: Float) {
        pickDragStarted = true
        pickDragStartTime = SystemClock.uptimeMillis()
        onPickStart?.invoke(downX, downY, fingerX, fingerY)
        handler.postDelayed(pickGestureLockRunnable, PICK_GESTURE_LOCK_MS)
    }

    private fun finishPick() {
        pickActive = false
        if (pickDragStarted) {
            onPickEnd?.invoke()
        }
    }

    private fun finishGestureOnly() {
        pickActive = false
        if (pickDragStarted) {
            onPickCancel?.invoke()
        } else {
            onPickPreviewCancel?.invoke()
        }
    }

    private fun cancelDeferredCallbacks() {
        handler.removeCallbacks(pickGestureLockRunnable)
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(singleTapRunnable)
    }

    private fun classifyTapRelease(upX: Float, upY: Float) {
        val now = SystemClock.uptimeMillis()
        val isDoubleTap = now - lastTapUpTime <= DOUBLE_TAP_MS &&
            hypot(upX - lastTapUpX, upY - lastTapUpY) <= slopPx * 2f
        if (isDoubleTap) {
            handler.removeCallbacks(singleTapRunnable)
            pendingSingleTap = false
            lastTapUpTime = 0L
            onGesture?.invoke(FloatBallGestureType.DOUBLE_TAP, upX, upY)
            return
        }
        lastTapUpTime = now
        lastTapUpX = upX
        lastTapUpY = upY
        pendingSingleTap = true
        pendingSingleTapX = upX
        pendingSingleTapY = upY
        handler.postDelayed(singleTapRunnable, DOUBLE_TAP_MS)
    }

    private fun lockSwipeAxisIfNeeded(totalDx: Float, totalDy: Float) {
        if (lockedSwipeAxis != null || isPickCommitLocked() || longPressFired) return
        val axis = resolveSwipeAxis(totalDx, totalDy) ?: return
        lockedSwipeAxis = axis
        lockedAxisForwardSign = forwardSignForAxis(axis, totalDx)
    }

    private fun forwardSignForAxis(axis: LockedSwipeAxis, totalDx: Float): Float =
        when (axis) {
            LockedSwipeAxis.UP -> -1f
            LockedSwipeAxis.DOWN -> 1f
            LockedSwipeAxis.SIDE -> if (totalDx >= 0f) 1f else -1f
        }

    internal fun updateGestureArmState(
        totalDx: Float,
        totalDy: Float,
        incrementalDx: Float,
        incrementalDy: Float,
        lockedAxis: LockedSwipeAxis? = lockedSwipeAxis,
        forwardSign: Float = lockedAxisForwardSign,
    ) {
        val axis = lockedAxis ?: run {
            gestureArmed = false
            peakForwardProgressPx = 0f
            return
        }
        if (isPickCommitLocked() || longPressFired) {
            gestureArmed = false
            return
        }
        val (projDx, projDy) = projectedDisplacement(totalDx, totalDy, axis)
        val forwardProgress = forwardProgressAlongAxis(axis, projDx, projDy, forwardSign)
        if (forwardProgress > peakForwardProgressPx) {
            peakForwardProgressPx = forwardProgress
        }
        gestureArmed = retainsGestureCommitment(forwardProgress) &&
            qualifiesAsSwipe(projDx, projDy)
    }

    internal fun isGestureArmedForTest(): Boolean = gestureArmed

    internal fun retainsGestureCommitment(forwardProgress: Float): Boolean {
        if (peakForwardProgressPx <= slopPx) {
            return forwardProgress > slopPx
        }
        return forwardProgress >= peakForwardProgressPx * GESTURE_CANCEL_RETAIN_FRACTION
    }

    private fun forwardProgressAlongAxis(
        axis: LockedSwipeAxis,
        projDx: Float,
        projDy: Float,
        forwardSign: Float,
    ): Float {
        val axisValue = when (axis) {
            LockedSwipeAxis.UP, LockedSwipeAxis.DOWN -> projDy
            LockedSwipeAxis.SIDE -> projDx
        }
        return axisValue * forwardSign
    }

    private fun forwardProgressAlongLockedAxis(projDx: Float, projDy: Float): Float {
        val axis = lockedSwipeAxis ?: return 0f
        return forwardProgressAlongAxis(axis, projDx, projDy, lockedAxisForwardSign)
    }

    private fun shouldCommitSwipeGesture(projDx: Float, projDy: Float): Boolean {
        if (isPickCommitLocked() || longPressFired) return false
        if (!qualifiesAsSwipe(projDx, projDy)) return false
        return retainsGestureCommitment(forwardProgressAlongLockedAxis(projDx, projDy))
    }

    internal fun resolveSwipeAxis(dx: Float, dy: Float): LockedSwipeAxis? {
        if (!qualifiesAsSwipe(dx, dy)) return null
        val absDx = abs(dx)
        val absDy = abs(dy)
        return when {
            absDy > absDx * DIRECTION_RATIO && dy < 0f -> LockedSwipeAxis.UP
            absDy > absDx * DIRECTION_RATIO && dy > 0f -> LockedSwipeAxis.DOWN
            absDx > absDy * DIRECTION_RATIO -> LockedSwipeAxis.SIDE
            else -> null
        }
    }

    internal fun projectedDisplacement(
        totalDx: Float,
        totalDy: Float,
        lockedAxis: LockedSwipeAxis?,
    ): Pair<Float, Float> {
        return when (lockedAxis) {
            LockedSwipeAxis.UP, LockedSwipeAxis.DOWN -> 0f to totalDy
            LockedSwipeAxis.SIDE -> totalDx to 0f
            null -> totalDx to totalDy
        }
    }

    private fun projectedDisplacement(totalDx: Float, totalDy: Float): Pair<Float, Float> =
        projectedDisplacement(totalDx, totalDy, lockedSwipeAxis)

    private fun qualifiesAsSwipe(dx: Float, dy: Float): Boolean {
        val absDx = abs(dx)
        val absDy = abs(dy)
        if (hypot(dx, dy) <= slopPx) return false
        return when {
            absDy > absDx * DIRECTION_RATIO && dy < 0f -> true
            absDy > absDx * DIRECTION_RATIO && dy > 0f -> true
            absDx > absDy * DIRECTION_RATIO -> true
            else -> false
        }
    }

    internal fun predictSwipeGesture(dx: Float, dy: Float): FloatBallGestureType? {
        if (isPickCommitLocked() || longPressFired) return null
        if (!qualifiesAsSwipe(dx, dy)) return null
        return classifySwipe(dx, dy)
    }

    private fun emitGestureHint(totalDx: Float, totalDy: Float) {
        if (!gestureArmed) {
            onGestureHint?.invoke(null)
            return
        }
        val (dx, dy) = projectedDisplacement(totalDx, totalDy)
        onGestureHint?.invoke(predictSwipeGesture(dx, dy))
    }

    private fun classifySwipe(dx: Float, dy: Float): FloatBallGestureType? {
        val absDx = abs(dx)
        val absDy = abs(dy)
        return when {
            absDy > absDx * DIRECTION_RATIO && dy < 0f -> {
                if (absDy > upSwipeShortPx) {
                    FloatBallGestureType.SWIPE_UP_LONG
                } else {
                    FloatBallGestureType.SWIPE_UP_SHORT
                }
            }
            absDy > absDx * DIRECTION_RATIO && dy > 0f -> {
                if (dy > downSwipeShortPx) {
                    FloatBallGestureType.SWIPE_DOWN_LONG
                } else {
                    FloatBallGestureType.SWIPE_DOWN_SHORT
                }
            }
            absDx > absDy * DIRECTION_RATIO -> {
                if (absDx > sideSwipeShortPx) {
                    FloatBallGestureType.SWIPE_SIDE_LONG
                } else {
                    FloatBallGestureType.SWIPE_SIDE_SHORT
                }
            }
            else -> null
        }
    }

    private fun cancelPendingSingleTap() {
        handler.removeCallbacks(singleTapRunnable)
        pendingSingleTap = false
    }

    private fun resetTouchSession() {
        launcherCaptureMode = false
        longPressFired = false
        movedBeyondSlop = false
        pickActive = false
        pickDragStarted = false
        pickDragStartTime = 0L
        pickGestureLocked = false
        pausePickLocked = false
        lockedSwipeAxis = null
        lockedAxisForwardSign = 0f
        gestureArmed = false
        peakForwardProgressPx = 0f
    }

    private fun swipeThresholdPx(percent: Float, density: Float): Float =
        (percent.coerceIn(50f, 500f) * SWIPE_BASE_DP * density) / 100f
}
