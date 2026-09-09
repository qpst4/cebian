package com.slideindex.app.overlay

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import com.slideindex.app.util.ContinuousAdjustController
import com.slideindex.app.util.VolumeControlHelper

internal class AdjustPanelRenderer(
    private val ctrl: AdjustPanelOverlayController
) {
    private val host get() = ctrl.host

    fun drawVisibleIndicator(canvas: Canvas) {
        if (ctrl.adjustIndicatorProgress <= 0f) return
        val visual = captureAdjustIndicatorVisual() ?: return
        ctrl.adjustIndicatorHoldVisual = visual
        drawAdjustIndicator(
            canvas = canvas,
            mode = visual.mode,
            fraction = visual.fraction,
            anchorRawY = visual.anchorRawY
        )
    }

    fun updateAdjustIndicatorLayout(
        anchorRawY: Float,
        forceFullScreenAnchor: Boolean = false,
        mode: ContinuousAdjustController.Mode? = null
    ): AdjustLevelIndicator.Layout? {
        val density = host.density()
        val screenWidthPx = host.screenWidthPx()
        val loc = host.viewLocationOnScreen()
        val anchorLocalY = host.anchorLocalY(anchorRawY)
        val adjustMode = mode
            ?: ctrl.adjustPanelState?.mode
            ?: host.gestureSession().adjustModeOrNull()
        ctrl.adjustIndicatorLayout = AdjustLevelIndicator.layout(
            viewWidth = if (forceFullScreenAnchor) screenWidthPx else host.viewWidth().coerceAtLeast(1),
            viewHeight = host.viewHeight().coerceAtLeast(host.screenHeightPx()),
            side = host.side(),
            anchorY = anchorLocalY,
            density = density,
            viewScreenX = if (forceFullScreenAnchor) 0 else loc[0],
            screenWidthPx = screenWidthPx,
            chrome = when (adjustMode) {
                ContinuousAdjustController.Mode.VOLUME -> AdjustPanelChrome.VOLUME
                ContinuousAdjustController.Mode.BRIGHTNESS -> AdjustPanelChrome.BRIGHTNESS
                null -> AdjustPanelChrome.NONE
            },
            volumeExpanded = ctrl.adjustPanelState?.volumeExpanded == true &&
                adjustMode == ContinuousAdjustController.Mode.VOLUME
        )
        return ctrl.adjustIndicatorLayout
    }

    fun continueAdjustPanelEnterAnimation(anchorRawY: Float) {
        ctrl.adjustPanelEntering = true
        ctrl.wasAdjustMode = true
        ctrl.adjustIndicatorReceding = false
        updateAdjustIndicatorLayout(
            anchorRawY,
            forceFullScreenAnchor = true,
            mode = ctrl.adjustPanelState?.mode
        )
        ctrl.adjustIndicatorFrozenLayout = ctrl.adjustIndicatorLayout
        val remaining = (1f - ctrl.adjustIndicatorProgress).coerceIn(0f, 1f)
        val durationMs = (ADJUST_INDICATOR_ENTER_MS * remaining).toLong().coerceAtLeast(1L)
        animateAdjustIndicatorTo(
            target = 1f,
            durationMs = durationMs,
            interpolator = DecelerateInterpolator()
        ) {
            ctrl.adjustPanelEntering = false
            ctrl.adjustIndicatorFrozenLayout = null
            updateAdjustIndicatorLayout(anchorRawY, mode = ctrl.adjustPanelState?.mode)
            host.invalidate()
        }
    }

    fun beginAdjustPanelEnterAnimation(anchorRawY: Float) {
        ctrl.adjustPanelEntering = true
        ctrl.wasAdjustMode = true
        ctrl.adjustIndicatorAnimator?.cancel()
        ctrl.adjustIndicatorReceding = false
        ctrl.adjustIndicatorProgress = 0f
        ctrl.adjustIndicatorFrozenLayout = null
        updateAdjustIndicatorLayout(
            anchorRawY,
            forceFullScreenAnchor = true,
            mode = ctrl.adjustPanelState?.mode
        )
        ctrl.adjustIndicatorFrozenLayout = ctrl.adjustIndicatorLayout
        animateAdjustIndicatorTo(
            target = 1f,
            durationMs = ADJUST_INDICATOR_ENTER_MS,
            interpolator = DecelerateInterpolator()
        ) {
            ctrl.adjustPanelEntering = false
            ctrl.adjustIndicatorFrozenLayout = null
            updateAdjustIndicatorLayout(anchorRawY, mode = ctrl.adjustPanelState?.mode)
            host.invalidate()
        }
    }

    fun startAdjustIndicatorEnterAnimationIfNeeded() {
        if (ctrl.adjustPanelState != null) return
        if (ctrl.adjustIndicatorProgress >= 1f) return
        if (ctrl.adjustIndicatorAnimator?.isRunning == true) return
        animateAdjustIndicatorTo(
            target = 1f,
            durationMs = ADJUST_INDICATOR_ENTER_MS,
            interpolator = DecelerateInterpolator()
        )
    }

    fun captureAdjustIndicatorVisualForDismiss(): AdjustPanelOverlayController.AdjustIndicatorVisual? {
        return captureAdjustIndicatorVisual()
    }

    fun freezeAdjustIndicatorLayout(anchorRawY: Float?, mode: ContinuousAdjustController.Mode? = null) {
        if (ctrl.adjustIndicatorFrozenLayout != null) return
        anchorRawY?.let { updateAdjustIndicatorLayout(it, mode = mode) }
        ctrl.adjustIndicatorFrozenLayout = ctrl.adjustIndicatorLayout
    }

    fun clearAdjustIndicatorExitState() {
        ctrl.adjustIndicatorHoldVisual = null
        ctrl.adjustIndicatorLayout = null
        ctrl.adjustIndicatorFrozenLayout = null
        ctrl.adjustIndicatorReceding = false
    }

    fun animateAdjustIndicatorTo(
        target: Float,
        durationMs: Long,
        interpolator: Interpolator = DecelerateInterpolator(),
        onEnd: (() -> Unit)? = null
    ) {
        ctrl.adjustIndicatorAnimator?.cancel()
        val receding = target == 0f && ctrl.adjustIndicatorProgress > 0f
        if (receding) {
            ctrl.adjustIndicatorReceding = true
            freezeAdjustIndicatorLayout(
                ctrl.adjustIndicatorHoldVisual?.anchorRawY ?: ctrl.adjustPanelState?.anchorRawY
                    ?: host.gestureSession().adjustAnchorRawY(),
                ctrl.adjustIndicatorHoldVisual?.mode
                    ?: ctrl.adjustPanelState?.mode
                    ?: host.gestureSession().adjustModeOrNull()
            )
        } else if (target >= 1f) {
            ctrl.adjustIndicatorReceding = false
            if (!ctrl.adjustPanelEntering) {
                ctrl.adjustIndicatorFrozenLayout = null
            }
        }
        if (durationMs <= 0L || ctrl.adjustIndicatorProgress == target) {
            ctrl.adjustIndicatorProgress = target
            if (target >= 1f) {
                ctrl.adjustIndicatorReceding = false
                if (!ctrl.adjustPanelEntering) {
                    ctrl.adjustIndicatorFrozenLayout = null
                }
            }
            host.invalidate()
            onEnd?.invoke()
            return
        }
        ctrl.adjustIndicatorAnimator = ValueAnimator.ofFloat(ctrl.adjustIndicatorProgress, target).apply {
            duration = durationMs
            this.interpolator = interpolator
            addUpdateListener { animator ->
                ctrl.adjustIndicatorProgress = animator.animatedValue as Float
                host.invalidate()
            }
            if (onEnd != null) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        onEnd()
                    }
                })
            }
            start()
        }
    }

    private fun captureAdjustIndicatorVisual(): AdjustPanelOverlayController.AdjustIndicatorVisual? {
        ctrl.adjustPanelState?.let { state ->
            val fraction = when (state.dragTarget) {
                VolumeDragTarget.MEDIA -> host.actionExecutor().adjustFraction()
                else -> state.fraction
            }
            return AdjustPanelOverlayController.AdjustIndicatorVisual(state.mode, fraction, state.anchorRawY)
        }
        host.gestureSession().adjustModeOrNull()?.let { mode ->
            return AdjustPanelOverlayController.AdjustIndicatorVisual(
                mode = mode,
                fraction = host.actionExecutor().adjustFraction(),
                anchorRawY = host.gestureSession().adjustAnchorRawY()
            )
        }
        return ctrl.adjustIndicatorHoldVisual
    }

    private fun resolveBrightnessPanelVisual(): BrightnessPanelVisual? {
        ctrl.adjustPanelState?.takeIf { it.mode == ContinuousAdjustController.Mode.BRIGHTNESS }?.let { state ->
            return BrightnessPanelVisual(
                autoBrightnessEnabled = state.autoBrightnessEnabled,
                darkModeEnabled = state.darkModeEnabled
            )
        }
        if (host.gestureSession().adjustModeOrNull() != ContinuousAdjustController.Mode.BRIGHTNESS) return null
        val executor = host.actionExecutor()
        return BrightnessPanelVisual(
            autoBrightnessEnabled = executor.readAutoBrightnessEnabled(),
            darkModeEnabled = executor.readDarkModeEnabled()
        )
    }

    private fun resolveVolumePanelVisual(): VolumePanelVisual? {
        ctrl.adjustPanelState?.takeIf { it.mode == ContinuousAdjustController.Mode.VOLUME }?.let { state ->
            return VolumePanelVisual(
                expanded = state.volumeExpanded,
                ringFraction = state.ringFraction,
                notificationFraction = state.notificationFraction,
                ringerMode = state.ringerMode,
                interruptionFilter = state.interruptionFilter
            )
        }
        if (host.gestureSession().adjustModeOrNull() != ContinuousAdjustController.Mode.VOLUME) return null
        val executor = host.actionExecutor()
        return VolumePanelVisual(
            expanded = false,
            ringFraction = executor.readVolumeFraction(VolumeControlHelper.Stream.RING),
            notificationFraction = executor.readVolumeFraction(VolumeControlHelper.Stream.NOTIFICATION),
            ringerMode = executor.readRingerMode(),
            interruptionFilter = executor.readInterruptionFilter()
        )
    }

    private fun drawAdjustIndicator(
        canvas: Canvas,
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        anchorRawY: Float
    ) {
        val layout = if (ctrl.adjustIndicatorReceding || ctrl.adjustPanelEntering) {
            ctrl.adjustIndicatorFrozenLayout ?: run {
                freezeAdjustIndicatorLayout(anchorRawY, mode)
                ctrl.adjustIndicatorFrozenLayout
            }
        } else {
            ctrl.adjustIndicatorFrozenLayout = null
            val panelVisible = ctrl.adjustPanelState != null && !ctrl.adjustPanelDismissing
            if (panelVisible && ctrl.adjustIndicatorLayout != null) {
                ctrl.adjustIndicatorLayout
            } else {
                updateAdjustIndicatorLayout(anchorRawY, mode = mode)
                ctrl.adjustIndicatorLayout
            }
        } ?: return
        val volumePanel = resolveVolumePanelVisual()
        val brightnessPanel = resolveBrightnessPanelVisual()
        AdjustLevelIndicator.draw(
            canvas = canvas,
            layout = layout,
            mode = mode,
            fraction = fraction,
            enterProgress = ctrl.adjustIndicatorProgress,
            density = host.density(),
            side = host.side(),
            recede = ctrl.adjustIndicatorReceding,
            volumePanel = volumePanel,
            brightnessPanel = brightnessPanel,
            context = host.context
        )
    }

    companion object {
        private const val ADJUST_INDICATOR_ENTER_MS = 220L
        const val ADJUST_INDICATOR_EXIT_MS = 160L
    }
}
