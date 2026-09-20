package com.slideindex.app.clipboardoverlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import com.slideindex.app.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Port of AOSP `screenshot/DraggableConstraintLayout`: swipe-to-dismiss with return animation.
 */
open class DraggableConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    interface SwipeDismissCallbacks {
        fun onInteraction() {}
        fun onSwipeDismissInitiated(animator: Animator) {}
        fun onDismissComplete() {}
    }

    private val displayMetrics = context.resources.displayMetrics
    private val screenWidthPx: Int =
        context.getSystemService(WindowManager::class.java)
            ?.currentWindowMetrics?.bounds?.width()
            ?: displayMetrics.widthPixels
    private val swipeDismissHandler = SwipeDismissHandler()
    private var actionsContainer: View? = null
    private var callbacks: SwipeDismissCallbacks = object : SwipeDismissCallbacks {}

    private val swipeDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                val container = actionsContainer ?: return true
                return !container.screenBounds().contains(e2.rawX.toInt(), e2.rawY.toInt()) ||
                    !container.canScrollHorizontally(distanceX.toInt())
            }
        },
    ).apply { setIsLongpressEnabled(false) }

    init {
        setOnTouchListener(swipeDismissHandler)
    }

    open fun setCallbacks(callbacks: SwipeDismissCallbacks) {
        this.callbacks = callbacks
    }

    override fun onInterceptHoverEvent(event: MotionEvent): Boolean {
        callbacks.onInteraction()
        return super.onInterceptHoverEvent(event)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        actionsContainer = findViewById(R.id.actions_container)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            swipeDismissHandler.onTouch(this, ev)
        }
        return swipeDetector.onTouchEvent(ev)
    }

    fun isDismissing(): Boolean = swipeDismissHandler.isDismissing()

    fun dismiss() {
        swipeDismissHandler.dismiss()
    }

    private fun getBackgroundRight(): Int {
        return findViewById<View>(R.id.actions_container_background)?.right ?: 0
    }

    private inner class SwipeDismissHandler : OnTouchListener {
        private val gestureDetector = GestureDetector(context, SwipeDismissGestureListener())
        private var dismissAnimation: ValueAnimator? = null
        private var startX = 0f
        private var directionX = 0
        private var previousX = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val gestureResult = gestureDetector.onTouchEvent(event)
            callbacks.onInteraction()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    previousX = startX
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (dismissAnimation?.isRunning == true) return true
                    if (isPastDismissThreshold()) {
                        val anim = createSwipeDismissAnimation()
                        callbacks.onSwipeDismissInitiated(anim)
                        dismiss(anim)
                    } else {
                        createSwipeReturnAnimation().start()
                    }
                    return true
                }
            }
            return gestureResult
        }

        private inner class SwipeDismissGestureListener : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                this@DraggableConstraintLayout.translationX = e2.rawX - startX
                directionX = if (e2.rawX < previousX) -1 else 1
                previousX = e2.rawX
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (translationX * velocityX > 0 && dismissAnimation?.isRunning != true) {
                    val dismissAnimator = createSwipeDismissAnimation(velocityX / 1000f)
                    callbacks.onSwipeDismissInitiated(dismissAnimator)
                    dismiss(dismissAnimator)
                    return true
                }
                return false
            }
        }

        private fun isPastDismissThreshold(): Boolean {
            val translationX = this@DraggableConstraintLayout.translationX
            if (translationX * directionX > 0) {
                return abs(translationX) >= dpToPx(DISMISS_DISTANCE_THRESHOLD_DP)
            }
            return false
        }

        fun isDismissing(): Boolean = dismissAnimation?.isRunning == true

        fun dismiss() {
            dismiss(createSwipeDismissAnimation())
        }

        private fun dismiss(animator: ValueAnimator) {
            dismissAnimation = animator
            animator.addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) callbacks.onDismissComplete()
                }
            })
            animator.start()
        }

        private fun createSwipeDismissAnimation(velocity: Float = dpToPx(VELOCITY_DP_PER_MS)): ValueAnimator {
            val clamped = min(3f, max(1f, velocity))
            val anim = ValueAnimator.ofFloat(0f, 1f)
            val start = translationX
            val layoutDir = resources.configuration.layoutDirection
            val finalX = if (start > 0f || (start == 0f && layoutDir == LAYOUT_DIRECTION_RTL)) {
                screenWidthPx.toFloat()
            } else {
                -1f * getBackgroundRight()
            }
            val distance = min(abs(finalX - start), dpToPx(MAXIMUM_DISMISS_DISTANCE_DP))
            val distanceVector = if (finalX - start >= 0f) distance else -distance
            anim.addUpdateListener { animation ->
                val translation = lerp(start, start + distanceVector, animation.animatedFraction)
                translationX = translation
                alpha = 1f - animation.animatedFraction
            }
            anim.interpolator = LinearInterpolator()
            anim.duration = abs(distance / clamped).toLong().coerceAtLeast(1L)
            return anim
        }

        private fun createSwipeReturnAnimation(): ValueAnimator {
            val anim = ValueAnimator.ofFloat(0f, 1f)
            val start = translationX
            anim.addUpdateListener { animation ->
                translationX = lerp(start, 0f, animation.animatedFraction)
            }
            return anim
        }
    }

    private fun dpToPx(dp: Float): Float =
        dp * displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()

    companion object {
        const val SWIPE_PADDING_DP = 12f
        private const val VELOCITY_DP_PER_MS = 1f
        private const val MAXIMUM_DISMISS_DISTANCE_DP = 400f
        private const val DISMISS_DISTANCE_THRESHOLD_DP = 20f

        private fun lerp(start: Float, stop: Float, amount: Float): Float =
            start + (stop - start) * amount
    }
}

internal fun View.screenBounds(): Rect {
    val loc = IntArray(2)
    getLocationOnScreen(loc)
    return Rect(loc[0], loc[1], loc[0] + width, loc[1] + height)
}

internal fun View.copyBoundsOnScreen(out: Rect) {
    val loc = IntArray(2)
    getLocationOnScreen(loc)
    out.set(loc[0], loc[1], loc[0] + width, loc[1] + height)
}
