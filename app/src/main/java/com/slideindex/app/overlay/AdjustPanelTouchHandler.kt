package com.slideindex.app.overlay

import android.view.MotionEvent
import com.slideindex.app.util.BrightnessControlHelper
import com.slideindex.app.util.ContinuousAdjustController
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.VolumeControlHelper

internal class AdjustPanelTouchHandler(
    private val ctrl: AdjustPanelOverlayController,
) {
    private val host get() = ctrl.host

    fun handleTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        if (ctrl.adjustPanelDismissing) return false
        val state = ctrl.adjustPanelState ?: return false
        val density = host.density()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val layout = ctrl.adjustIndicatorLayout ?: run {
                    ctrl.dismissAdjustPanel()
                    return true
                }
                when (state.mode) {
                    ContinuousAdjustController.Mode.VOLUME -> when (
                        AdjustLevelIndicator.hitVolumeTarget(layout, host.side(), localX, localY, density)
                    ) {
                        VolumeHitTarget.DND -> {
                            if (!VolumeControlHelper.hasAccess(host.context)) {
                                PermissionHelper.requestNotificationPolicyAccess(host.context)
                            } else {
                                host.actionExecutor().toggleDnd()?.let { state.interruptionFilter = it }
                                host.hapticConfirmLaunch()
                            }
                            host.invalidate()
                            return true
                        }
                        VolumeHitTarget.RINGER -> {
                            if (!VolumeControlHelper.hasAccess(host.context)) {
                                PermissionHelper.requestNotificationPolicyAccess(host.context)
                            } else {
                                host.actionExecutor().cycleRingerMode()?.let { state.ringerMode = it }
                                host.hapticConfirmLaunch()
                            }
                            host.invalidate()
                            return true
                        }
                        VolumeHitTarget.EXPAND -> {
                            state.volumeExpanded = !state.volumeExpanded
                            if (state.volumeExpanded) {
                                refreshVolumePanelLevels(state)
                            }
                            ctrl.updateAdjustIndicatorLayout(state.anchorRawY)
                            host.hapticConfirmLaunch()
                            host.invalidate()
                            return true
                        }
                        VolumeHitTarget.MEDIA -> {
                            beginMainAdjustDrag(state, event.rawY)
                            return true
                        }
                        VolumeHitTarget.RING -> {
                            if (!state.volumeExpanded) return false
                            beginVolumeStreamDrag(state, VolumeDragTarget.RING, event.rawY)
                            host.invalidate()
                            return true
                        }
                        VolumeHitTarget.NOTIFICATION -> {
                            if (!state.volumeExpanded) return false
                            beginVolumeStreamDrag(state, VolumeDragTarget.NOTIFICATION, event.rawY)
                            host.invalidate()
                            return true
                        }
                        VolumeHitTarget.NONE -> {
                            ctrl.dismissAdjustPanel()
                            return true
                        }
                    }
                    ContinuousAdjustController.Mode.BRIGHTNESS -> when (
                        AdjustLevelIndicator.hitBrightnessTarget(layout, host.side(), localX, localY, density)
                    ) {
                        BrightnessHitTarget.AUTO_BRIGHTNESS -> {
                            if (!BrightnessControlHelper.hasAccess(host.context)) {
                                PermissionHelper.requestWriteSettingsAccess(host.context)
                            } else {
                                host.actionExecutor().toggleAutoBrightness()?.let {
                                    state.autoBrightnessEnabled = it
                                    ctrl.syncBrightnessFractionAfterAutoToggle()
                                    host.hapticConfirmLaunch()
                                }
                            }
                            host.invalidate()
                            return true
                        }
                        BrightnessHitTarget.DARK_MODE -> {
                            if (!BrightnessControlHelper.hasDarkModeAccess(host.context)) {
                                PermissionHelper.requestWriteSettingsAccess(host.context)
                            } else {
                                host.actionExecutor().toggleDarkMode()?.let {
                                    state.darkModeEnabled = it
                                    host.hapticConfirmLaunch()
                                }
                            }
                            host.invalidate()
                            return true
                        }
                        BrightnessHitTarget.BRIGHTNESS -> {
                            beginMainAdjustDrag(state, event.rawY)
                            return true
                        }
                        BrightnessHitTarget.NONE -> {
                            ctrl.dismissAdjustPanel()
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (state.dragTarget == null) return false
                when (state.dragTarget) {
                    VolumeDragTarget.MEDIA -> {
                        host.actionExecutor().updateContinuousAdjust(state.mode, event.rawY)
                        state.fraction = host.actionExecutor().adjustFraction()
                    }
                    VolumeDragTarget.RING, VolumeDragTarget.NOTIFICATION -> {
                        updateVolumeStreamDrag(state, event.rawY)
                    }
                    null -> return false
                }
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (state.dragTarget == null) return false
                when (state.dragTarget) {
                    VolumeDragTarget.MEDIA -> {
                        if (state.mode == ContinuousAdjustController.Mode.BRIGHTNESS) {
                            ctrl.suppressBrightnessLevelSync()
                        }
                        val committedFraction = if (state.mode == ContinuousAdjustController.Mode.BRIGHTNESS) {
                            host.actionExecutor().adjustFraction()
                        } else {
                            null
                        }
                        host.actionExecutor().endContinuousAdjust()
                        state.fraction = committedFraction
                            ?: host.actionExecutor().readCurrentAdjustFraction(state.mode)
                    }
                    VolumeDragTarget.RING, VolumeDragTarget.NOTIFICATION -> Unit
                    null -> Unit
                }
                state.dragTarget = null
                state.dragging = false
                host.invalidate()
                return true
            }
        }
        return false
    }

    fun refreshVolumePanelLevels(state: AdjustPanelState) {
        val executor = host.actionExecutor()
        state.fraction = executor.readCurrentAdjustFraction(ContinuousAdjustController.Mode.VOLUME)
        state.ringFraction = executor.readVolumeFraction(VolumeControlHelper.Stream.RING)
        state.notificationFraction = executor.readVolumeFraction(VolumeControlHelper.Stream.NOTIFICATION)
        state.ringerMode = executor.readRingerMode()
        state.interruptionFilter = executor.readInterruptionFilter()
    }

    private fun beginMainAdjustDrag(state: AdjustPanelState, rawY: Float) {
        state.dragTarget = VolumeDragTarget.MEDIA
        state.dragging = true
        host.actionExecutor().beginContinuousAdjust(state.mode, rawY)
        host.actionExecutor().updateContinuousAdjust(state.mode, rawY)
        host.invalidate()
    }

    private fun beginVolumeStreamDrag(state: AdjustPanelState, target: VolumeDragTarget, rawY: Float) {
        state.dragTarget = target
        state.dragging = true
        ctrl.volumeDragAnchorRawY = rawY
        ctrl.volumeDragBaseline = when (target) {
            VolumeDragTarget.RING -> state.ringFraction
            VolumeDragTarget.NOTIFICATION -> state.notificationFraction
            VolumeDragTarget.MEDIA -> state.fraction
        }
    }

    private fun updateVolumeStreamDrag(state: AdjustPanelState, rawY: Float) {
        val span = host.screenHeightPx().coerceAtLeast(1) * VOLUME_DRAG_SPAN_SCREEN_FRACTION
        val fraction = ((ctrl.volumeDragBaseline + (ctrl.volumeDragAnchorRawY - rawY) / span)).coerceIn(0f, 1f)
        when (state.dragTarget) {
            VolumeDragTarget.RING -> {
                state.ringFraction = fraction
                host.actionExecutor().setVolumeFraction(VolumeControlHelper.Stream.RING, fraction)
            }
            VolumeDragTarget.NOTIFICATION -> {
                state.notificationFraction = fraction
                host.actionExecutor().setVolumeFraction(VolumeControlHelper.Stream.NOTIFICATION, fraction)
            }
            VolumeDragTarget.MEDIA, null -> Unit
        }
    }

    companion object {
        private const val VOLUME_DRAG_SPAN_SCREEN_FRACTION = 0.5f
    }
}
