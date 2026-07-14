package com.slideindex.app.overlay.animation

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
    var isActive by mutableStateOf(false)
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

    fun onDragStart(rawX: Float, rawY: Float) {
        animJob?.cancel()
        isActive = true
        val position = GestureAnimationPosition.fromPanelSide(side)
        button = GestureAnimationButton(position)
        origin = Offset(rawX, rawY)
        finger = Offset(rawX, rawY)
        triggerDirection = GestureAnimationTriggerDirection.Center2

        animJob = scope.launch {
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
                }
            }
        }
    }

    fun onDrag(rawX: Float, rawY: Float, swipeDirection: SwipeDirection?, inwardPx: Float) {
        if (!isActive) return
        val dragAmount = Offset(rawX - finger.x, rawY - finger.y)
        finger = Offset(rawX, rawY)

        val longDistance = inwardPx >= longTriggerDistancePx
        triggerDirection = swipeDirection.toGestureTriggerDirection(longDistance)

        animJob = scope.launch {
            animMutex.withLock {
                fingerXAnim.snapTo(fingerXAnimVal + dragAmount.x)
                fingerYAnim.snapTo(fingerYAnimVal + dragAmount.y)
            }
        }
    }

    fun onDragEnd() {
        reset(endInteraction = true)
    }

    fun onDragCancel() {
        reset(endInteraction = true)
    }

    private fun reset(endInteraction: Boolean) {
        if (endInteraction) {
            isActive = false
        }
        origin = Offset.Unspecified
        finger = Offset.Unspecified
        triggerDirection = GestureAnimationTriggerDirection.Center2

        val position = button?.position ?: run {
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
                    GestureAnimationPosition.Bottom -> {
                        fingerYAnim.animateTo(0f, animationSpec)
                        fingerXAnim.animateTo(originXAnimVal, animationSpec)
                    }
                }
            }
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

        val slideDistance = when (direction) {
            GestureAnimationTriggerDirection.Up2, GestureAnimationTriggerDirection.Down2 ->
                when (target.position) {
                    GestureAnimationPosition.Left, GestureAnimationPosition.Right -> originY - fingerY
                    GestureAnimationPosition.Bottom -> fingerX - originX
                }
            else ->
                when (target.position) {
                    GestureAnimationPosition.Left -> fingerX - originX
                    GestureAnimationPosition.Right -> originX - fingerX
                    GestureAnimationPosition.Bottom -> originY - fingerY
                }
        }

        if (slideDistance < 0 &&
            direction != GestureAnimationTriggerDirection.Up2 &&
            direction != GestureAnimationTriggerDirection.Down2
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
                val edge2 = when (target.position) {
                    GestureAnimationPosition.Left, GestureAnimationPosition.Right -> abs(fingerY - originY)
                    GestureAnimationPosition.Bottom -> abs(fingerX - originX)
                }
                hypot(slideDistance.toDouble(), edge2.toDouble()) >= threshold
            }
            GestureAnimationTriggerDirection.Click -> false
        }
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
                GestureAnimationPosition.Bottom -> stickySlidePx
            }
        } else {
            stickySlidePx
        }
    }
}
