package com.slideindex.app.overlay.animation

/*
 * Portions derived from SideGesture (https://github.com/aaronzzx/gulugulu)
 * Licensed under Apache-2.0. Modified for com.slideindex.app.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.slideindex.app.gesture.SwipeDirection
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.WaveStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Animation state ported from SideGesture [SideGestureState] — position tracking only.
 */
class GestureAnimationState(
    private val scope: CoroutineScope,
    private val side: PanelSide,
) {
    var button: GestureAnimationButton? by mutableStateOf(null)
        private set
    var triggerDirection: GestureAnimationTriggerDirection by mutableStateOf(GestureAnimationTriggerDirection.Center2)
        private set
    var swipeDirection: SwipeDirection? by mutableStateOf(null)
        private set
    var isActive by mutableStateOf(false)
        private set

    /** Bumps on every anim frame so Canvas recomposes when [Animatable] values change. */
    internal var redrawTick by mutableStateOf(0)
        private set

    private var origin = Offset.Unspecified
    private var finger = Offset.Unspecified

    val originXAnimVal: Float get() = originXAnim.value
    val originYAnimVal: Float get() = originYAnim.value
    val fingerXAnimVal: Float get() = fingerXAnim.value
    val fingerYAnimVal: Float get() = fingerYAnim.value

    private val originXAnim = Animatable(Float.NaN)
    private val originYAnim = Animatable(Float.NaN)
    private val fingerXAnim = Animatable(Float.NaN)
    private val fingerYAnim = Animatable(Float.NaN)

    private val animationSpec = spring<Float>(stiffness = 3000f)
    private val animMutex = Mutex()
    private var animJob: Job? = null

    var shortTriggerDistancePx: Float = 0f
    var longTriggerDistancePx: Float = 0f
    var stickySlideEnabled: Boolean = false
    var stickySlidePx: Float = 0f

    var hintFingerOffsetPx: Float = 0f

    private fun markRedraw() {
        redrawTick++
    }

    /** 仅用于绘制：相对手指的视觉偏移（屏幕 Y）；触发改动仍按真实手指位置。 */
    fun displayYOffset(position: GestureAnimationPosition): Float {
        if (hintFingerOffsetPx <= 0f) return 0f
        return when (position) {
            GestureAnimationPosition.Top -> hintFingerOffsetPx
            GestureAnimationPosition.Left,
            GestureAnimationPosition.Right,
            GestureAnimationPosition.Bottom,
            -> -hintFingerOffsetPx
        }
    }

    fun onDragStart(rawX: Float, rawY: Float) {
        animJob?.cancel()
        isActive = true
        val position = GestureAnimationPosition.fromPanelSide(side)
        button = GestureAnimationButton(position)
        origin = Offset(rawX, rawY)
        finger = Offset(rawX, rawY)
        triggerDirection = GestureAnimationTriggerDirection.Center2
        swipeDirection = null

        animJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animMutex.withLock {
                originXAnim.snapTo(rawX)
                originYAnim.snapTo(rawY)
                when (position) {
                    GestureAnimationPosition.Left, GestureAnimationPosition.Right -> {
                        fingerXAnim.snapTo(stickySlideOffset(position, horizontal = true))
                        fingerYAnim.snapTo(rawY)
                    }
                    GestureAnimationPosition.Bottom -> {
                        fingerXAnim.snapTo(rawX)
                        fingerYAnim.snapTo(stickySlideOffset(position, horizontal = false))
                    }
                    GestureAnimationPosition.Top -> {
                        fingerXAnim.snapTo(rawX)
                        fingerYAnim.snapTo(stickySlideOffset(position, horizontal = false))
                    }
                }
                markRedraw()
            }
        }
    }

    fun onDrag(rawX: Float, rawY: Float, swipeDirection: SwipeDirection?, inwardPx: Float) {
        if (!isActive) return
        val dragAmount = Offset(rawX - finger.x, rawY - finger.y)
        finger = Offset(rawX, rawY)

        val longDistance = inwardPx >= longTriggerDistancePx
        this.swipeDirection = swipeDirection
        triggerDirection = swipeDirection.toGestureTriggerDirection(longDistance)

        animJob = scope.launch {
            animMutex.withLock {
                fingerXAnim.snapTo(fingerXAnimVal + dragAmount.x)
                fingerYAnim.snapTo(fingerYAnimVal + dragAmount.y)
                markRedraw()
            }
        }
    }

    fun onDragEnd() {
        reset(endInteraction = true)
    }

    fun onDragCancel() {
        reset(endInteraction = true)
    }

    /** 无收束动画地清除（单击等不应出现手势提示时）。 */
    fun cancelSilently() {
        animJob?.cancel()
        isActive = false
        origin = Offset.Unspecified
        finger = Offset.Unspecified
        triggerDirection = GestureAnimationTriggerDirection.Center2
        swipeDirection = null
        button = null
        scope.launch {
            animMutex.withLock {
                originXAnim.snapTo(Float.NaN)
                originYAnim.snapTo(Float.NaN)
                fingerXAnim.snapTo(Float.NaN)
                fingerYAnim.snapTo(Float.NaN)
                markRedraw()
            }
        }
    }

    private fun reset(endInteraction: Boolean) {
        if (endInteraction) {
            isActive = false
        }
        origin = Offset.Unspecified
        finger = Offset.Unspecified

        val position = button?.position ?: run {
            triggerDirection = GestureAnimationTriggerDirection.Center2
            swipeDirection = null
            clearAnimValues()
            return
        }
        animJob?.cancel()
        animJob = scope.launch {
            animMutex.withLock {
                when (position) {
                    GestureAnimationPosition.Left, GestureAnimationPosition.Right -> {
                        fingerXAnim.animateTo(0f, animationSpec)
                        fingerYAnim.animateTo(originYAnimVal, animationSpec)
                    }
                    GestureAnimationPosition.Bottom, GestureAnimationPosition.Top -> {
                        fingerYAnim.animateTo(0f, animationSpec)
                        fingerXAnim.animateTo(originXAnimVal, animationSpec)
                    }
                }
                markRedraw()
            }
            triggerDirection = GestureAnimationTriggerDirection.Center2
            swipeDirection = null
            clearAnimValues()
        }
    }

    private fun clearAnimValues() {
        scope.launch {
            animMutex.withLock {
                originXAnim.snapTo(Float.NaN)
                originYAnim.snapTo(Float.NaN)
                fingerXAnim.snapTo(Float.NaN)
                fingerYAnim.snapTo(Float.NaN)
                markRedraw()
            }
        }
        button = null
    }

    fun canDistanceTriggered(target: GestureAnimationButton, isLongSlide: Boolean): Boolean {
        if (!isActive) return false
        val originX = origin.x
        val originY = origin.y
        val fingerX = finger.x + stickySlideOffset(target.position, horizontal = true)
        val fingerY = finger.y + stickySlideOffset(target.position, horizontal = false)
        val direction = triggerDirection

        if (direction == GestureAnimationTriggerDirection.Center2) {
            return false
        }

        val slideDistance = slideDistanceFor(
            position = target.position,
            direction = direction,
            originX = originX,
            originY = originY,
            fingerX = fingerX,
            fingerY = fingerY,
        )

        if (slideDistance < 0 &&
            !direction.isDiagonalAlongEdge(target.position)
        ) {
            return false
        }

        val threshold = if (isLongSlide) longTriggerDistancePx else shortTriggerDistancePx
        return when (direction) {
            GestureAnimationTriggerDirection.Center, GestureAnimationTriggerDirection.Center2 ->
                slideDistance >= threshold
            GestureAnimationTriggerDirection.Up, GestureAnimationTriggerDirection.Down,
            GestureAnimationTriggerDirection.Up2, GestureAnimationTriggerDirection.Down2,
            -> {
                val edge2 = secondarySlideDistanceFor(
                    position = target.position,
                    direction = direction,
                    originX = originX,
                    originY = originY,
                    fingerX = fingerX,
                    fingerY = fingerY,
                )
                hypot(slideDistance.toDouble(), edge2.toDouble()) >= threshold
            }
            GestureAnimationTriggerDirection.Click -> false
        }
    }

    private fun slideDistanceFor(
        position: GestureAnimationPosition,
        direction: GestureAnimationTriggerDirection,
        originX: Float,
        originY: Float,
        fingerX: Float,
        fingerY: Float,
    ): Float = when (position) {
        GestureAnimationPosition.Top, GestureAnimationPosition.Bottom -> when (direction) {
            GestureAnimationTriggerDirection.Up, GestureAnimationTriggerDirection.Up2 ->
                originX - fingerX
            GestureAnimationTriggerDirection.Down, GestureAnimationTriggerDirection.Down2 ->
                fingerX - originX
            GestureAnimationTriggerDirection.Center, GestureAnimationTriggerDirection.Center2 ->
                if (position == GestureAnimationPosition.Top) {
                    fingerY - originY
                } else {
                    originY - fingerY
                }
            GestureAnimationTriggerDirection.Click -> 0f
        }
        GestureAnimationPosition.Left -> when (direction) {
            GestureAnimationTriggerDirection.Up, GestureAnimationTriggerDirection.Up2 ->
                originY - fingerY
            GestureAnimationTriggerDirection.Down, GestureAnimationTriggerDirection.Down2 ->
                fingerY - originY
            GestureAnimationTriggerDirection.Center, GestureAnimationTriggerDirection.Center2 ->
                fingerX - originX
            GestureAnimationTriggerDirection.Click -> 0f
        }
        GestureAnimationPosition.Right -> when (direction) {
            GestureAnimationTriggerDirection.Up, GestureAnimationTriggerDirection.Up2 ->
                originY - fingerY
            GestureAnimationTriggerDirection.Down, GestureAnimationTriggerDirection.Down2 ->
                fingerY - originY
            GestureAnimationTriggerDirection.Center, GestureAnimationTriggerDirection.Center2 ->
                originX - fingerX
            GestureAnimationTriggerDirection.Click -> 0f
        }
    }

    private fun secondarySlideDistanceFor(
        position: GestureAnimationPosition,
        direction: GestureAnimationTriggerDirection,
        originX: Float,
        originY: Float,
        fingerX: Float,
        fingerY: Float,
    ): Float = when (position) {
        GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
            abs(fingerY - originY)
        GestureAnimationPosition.Bottom, GestureAnimationPosition.Top -> when (direction) {
            GestureAnimationTriggerDirection.Center, GestureAnimationTriggerDirection.Center2 ->
                abs(fingerX - originX)
            else ->
                if (position == GestureAnimationPosition.Top) {
                    abs(fingerY - originY)
                } else {
                    abs(fingerY - originY)
                }
        }
    }

    private fun GestureAnimationTriggerDirection.isDiagonalAlongEdge(
        position: GestureAnimationPosition,
    ): Boolean = when (position) {
        GestureAnimationPosition.Top, GestureAnimationPosition.Bottom,
        GestureAnimationPosition.Left, GestureAnimationPosition.Right,
        ->
            this == GestureAnimationTriggerDirection.Up2 ||
                this == GestureAnimationTriggerDirection.Down2
    }

    fun applyWaveStyle(waveStyle: WaveStyle) {
        stickySlideEnabled = waveStyle.stickySlideEnabled
        stickySlidePx = if (waveStyle.stickySlideEnabled) waveStyle.stickySlidePx.toFloat() else 0f
    }

    private fun stickySlideOffset(position: GestureAnimationPosition, horizontal: Boolean): Float {
        if (!stickySlideEnabled || stickySlidePx <= 0f) return 0f
        return if (horizontal) {
            when (position) {
                GestureAnimationPosition.Left -> -stickySlidePx
                GestureAnimationPosition.Right -> stickySlidePx
                GestureAnimationPosition.Bottom, GestureAnimationPosition.Top -> stickySlidePx
            }
        } else {
            when (position) {
                GestureAnimationPosition.Bottom -> stickySlidePx
                GestureAnimationPosition.Top -> -stickySlidePx
                GestureAnimationPosition.Left, GestureAnimationPosition.Right -> stickySlidePx
            }
        }
    }
}
