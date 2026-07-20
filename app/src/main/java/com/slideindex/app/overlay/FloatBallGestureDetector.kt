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
    /** 超过手势窗口后为 true，抬手走取词而非手势。 */
    private var pickGestureLocked = false
    /** 首次滑出方向锁定后，仅沿该轴累计位移用于提示/抬手判定。 */
    private var lockedSwipeAxis: LockedSwipeAxis? = null
    private var longPressFired = false
    private var pendingSingleTap = false
    private var pendingSingleTapX = 0f
    private var pendingSingleTapY = 0f

    private var onPickStart: ((screenX: Float, screenY: Float) -> Unit)? = null
    private var onPickDrag: ((dx: Float, dy: Float) -> Unit)? = null
    private var onPickEnd: (() -> Unit)? = null
    private var onPickCancel: (() -> Unit)? = null
    private var onGesture: ((FloatBallGestureType, rawX: Float, rawY: Float) -> Unit)? = null
    private var onGestureHint: ((FloatBallGestureType?) -> Unit)? = null

    private val pickGestureLockRunnable = Runnable {
        pickGestureLocked = true
        onGestureHint?.invoke(null)
    }

    private val longPressRunnable = Runnable {
        if (!pickGestureLocked && !longPressFired) {
            val dist = hypot(lastX - downX, lastY - downY)
            if (dist <= slopPx * 2f) {
                longPressFired = true
                pendingSingleTap = false
                onGesture?.invoke(FloatBallGestureType.LONG_PRESS, lastX, lastY)
            }
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
        onPickStart: (screenX: Float, screenY: Float) -> Unit,
        onPickDrag: (dx: Float, dy: Float) -> Unit,
        onPickEnd: () -> Unit,
        onPickCancel: () -> Unit,
        onGesture: (FloatBallGestureType, rawX: Float, rawY: Float) -> Unit,
        onGestureHint: (FloatBallGestureType?) -> Unit = {},
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
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetSession()
                downX = event.rawX
                downY = event.rawY
                lastX = downX
                lastY = downY
                downTime = SystemClock.uptimeMillis()
                pickActive = true
                onGestureHint?.invoke(null)
                onPickStart?.invoke(downX, downY)
                handler.postDelayed(pickGestureLockRunnable, PICK_GESTURE_LOCK_MS)
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pickActive) return true
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                lastX = event.rawX
                lastY = event.rawY
                onPickDrag?.invoke(dx, dy)
                val distFromStart = hypot(event.rawX - downX, event.rawY - downY)
                if (distFromStart > slopPx) {
                    handler.removeCallbacks(longPressRunnable)
                    pendingSingleTap = false
                }
                val totalDx = event.rawX - downX
                val totalDy = event.rawY - downY
                lockSwipeAxisIfNeeded(totalDx, totalDy)
                emitGestureHint(totalDx, totalDy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                onGestureHint?.invoke(null)
                cancelDeferredCallbacks()
                val totalDx = event.rawX - downX
                val totalDy = event.rawY - downY
                lockSwipeAxisIfNeeded(totalDx, totalDy)
                val (dx, dy) = projectedDisplacement(totalDx, totalDy)
                val totalDist = hypot(dx, dy)
                val elapsed = SystemClock.uptimeMillis() - downTime
                val locked = pickGestureLocked || elapsed >= PICK_GESTURE_LOCK_MS
                when {
                    locked -> finishPick()
                    longPressFired -> finishGestureOnly()
                    qualifiesAsSwipe(dx, dy) -> {
                        classifySwipe(dx, dy)
                            ?.let { onGesture?.invoke(it, event.rawX, event.rawY) }
                        finishGestureOnly()
                    }
                    totalDist > slopPx -> finishGestureOnly()
                    else -> {
                        classifyTapRelease(event.rawX, event.rawY)
                        finishGestureOnly()
                    }
                }
                resetSession()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                onGestureHint?.invoke(null)
                cancelDeferredCallbacks()
                if (pickActive) {
                    pickActive = false
                    onPickCancel?.invoke()
                }
                resetSession()
                return true
            }
        }
        return false
    }

    fun cancel() {
        cancelDeferredCallbacks()
        if (pickActive) {
            pickActive = false
            onPickCancel?.invoke()
        }
        resetSession()
    }

    private fun finishPick() {
        pickActive = false
        onPickEnd?.invoke()
    }

    private fun finishGestureOnly() {
        pickActive = false
        onPickCancel?.invoke()
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
        if (lockedSwipeAxis != null || pickGestureLocked || longPressFired) return
        lockedSwipeAxis = resolveSwipeAxis(totalDx, totalDy)
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
        if (pickGestureLocked || longPressFired) return null
        if (!qualifiesAsSwipe(dx, dy)) return null
        return classifySwipe(dx, dy)
    }

    private fun emitGestureHint(totalDx: Float, totalDy: Float) {
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

    private fun resetSession() {
        longPressFired = false
        pendingSingleTap = false
        pickActive = false
        pickGestureLocked = false
        lockedSwipeAxis = null
    }

    private fun swipeThresholdPx(percent: Float, density: Float): Float =
        (percent.coerceIn(50f, 500f) * SWIPE_BASE_DP * density) / 100f
}
