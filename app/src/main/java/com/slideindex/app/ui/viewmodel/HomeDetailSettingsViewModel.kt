package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerDesignPreset
import com.slideindex.app.gesture.TriggerRectanglePresetLogic
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.gesture.GestureAngles
import com.slideindex.app.settings.BubbleStyle
import com.slideindex.app.settings.CapsuleStyle
import com.slideindex.app.settings.GestureHintStyle
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.settings.WaveStyle
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class HomeDetailSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    private val triggerDesignWriteMutex = Mutex()

    fun setIndexHeightFraction(value: Float) = launchSettingsWrite {
        settingsRepository.setIndexHeightFraction(value)
    }

    fun setAppsPerRow(value: Int) = launchSettingsWrite {
        settingsRepository.setAppsPerRow(value)
    }

    fun setPanelOpacity(value: Float) = launchSettingsWrite {
        settingsRepository.setPanelOpacity(value)
    }

    fun setDebugPerformanceMonitorEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setDebugPerformanceMonitorEnabled(enabled)
    }

    fun addHiddenApp(packageName: String) = launchSettingsWrite {
        settingsRepository.addHiddenApp(packageName)
    }

    fun removeHiddenApp(packageName: String) = launchSettingsWrite {
        settingsRepository.removeHiddenApp(packageName)
    }

    fun addExcludedTriggerApp(packageName: String) = launchSettingsWrite {
        settingsRepository.addExcludedTriggerApp(packageName)
    }

    fun removeExcludedTriggerApp(packageName: String) = launchSettingsWrite {
        settingsRepository.removeExcludedTriggerApp(packageName)
    }

    fun setFreeWindowEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFreeWindowEnabled(enabled)
    }

    fun setAppLaunchPolicyId(policyId: Int) = launchSettingsWrite {
        settingsRepository.setAppLaunchPolicyId(policyId)
    }

    fun setLongPressLaunchDurationMs(durationMs: Int) = launchSettingsWrite {
        settingsRepository.setLongPressLaunchDurationMs(durationMs)
    }

    fun setFreeWindowModeId(modeId: Int) = launchSettingsWrite {
        settingsRepository.setFreeWindowModeId(modeId)
    }

    fun setFreeWindowLayout(width: Float, height: Float, left: Float, top: Float) = launchSettingsWrite {
        settingsRepository.setFreeWindowLayout(width, height, left, top)
    }

    fun addBottomTriggerHandle() = launchSettingsWrite {
        settingsRepository.addBottomTriggerHandle()
    }

    fun addTopTriggerHandle() = launchSettingsWrite {
        settingsRepository.addTopTriggerHandle()
    }

    fun addTriggerHandlePair() = launchSettingsWrite {
        settingsRepository.addTriggerHandlePair()
    }

    fun removeTriggerHandle(side: PanelSide, handleId: String) = launchSettingsWrite {
        settingsRepository.removeTriggerHandle(side, handleId)
    }

    fun setSlotConfig(
        side: PanelSide,
        trigger: GestureTriggerType,
        action: GestureAction,
        mode: GestureTriggerMode,
        handleId: String,
    ) = launchSettingsWrite {
        settingsRepository.setSlotConfig(side, trigger, action, mode, handleId)
    }

    fun setDefaultTriggerMode(side: PanelSide, mode: GestureTriggerMode) = launchSettingsWrite {
        settingsRepository.setDefaultTriggerMode(side, mode)
    }

    fun setShortSwipeDistanceDp(side: PanelSide, handleId: String, value: Float) = launchSettingsWrite {
        settingsRepository.setShortSwipeDistanceDp(side, handleId, value)
    }

    fun setLongSwipeDistanceDp(side: PanelSide, handleId: String, value: Float) = launchSettingsWrite {
        settingsRepository.setLongSwipeDistanceDp(side, handleId, value)
    }

    fun setEdgeTriggerWidthDp(side: PanelSide, value: Float) = launchSettingsWrite {
        settingsRepository.setEdgeTriggerWidthDp(side, value)
    }

    fun setTriggerEdgeWidthDp(side: PanelSide, handleId: String, value: Float) = launchSettingsWrite {
        settingsRepository.setTriggerEdgeWidthDp(side, handleId, value)
    }

    fun setTriggerVerticalRange(side: PanelSide, handleId: String, top: Float, bottom: Float) =
        launchSettingsWrite {
            settingsRepository.setTriggerVerticalRange(side, handleId, top, bottom)
        }

    fun setTriggerHandleEnabled(side: PanelSide, handleId: String, enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setTriggerHandleEnabled(side, handleId, enabled)
    }

    fun setTriggerAlignOppositeSide(handleId: String, sourceSide: PanelSide, enabled: Boolean) =
        launchSettingsWrite {
            settingsRepository.setTriggerAlignOppositeSide(
                handleId = handleId,
                sourceSide = sourceSide,
                enabled = enabled,
            )
        }

    fun setTriggerAlignOppositeDesign(handleId: String, sourceSide: PanelSide, enabled: Boolean) =
        launchSettingsWrite {
            settingsRepository.setTriggerAlignOppositeDesign(
                handleId = handleId,
                sourceSide = sourceSide,
                enabled = enabled,
            )
        }

    fun setInterceptSystemBackGesture(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setInterceptSystemBackGesture(enabled)
    }

    fun setLimitMaxInterceptLength(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setLimitMaxInterceptLength(enabled)
    }

    fun setTriggerHandleDesign(side: PanelSide, handleId: String, design: TriggerHandleDesign) =
        launchRepositoryWrite {
            triggerDesignWriteMutex.withLock {
                settingsRepository.setTriggerHandleDesign(side, handleId, design)
            }
        }

    fun applyTriggerDesignPreset(side: PanelSide, handleId: String, preset: TriggerDesignPreset) =
        launchRepositoryWrite {
            triggerDesignWriteMutex.withLock {
                settingsRepository.applyTriggerDesignPreset(side, handleId, preset)
            }
        }

    suspend fun saveGestureAngles(angles: GestureAngles): Boolean =
        settingsRepository.setGestureAngles(angles)
            .onFailure {
                userMessageBus.showError(appContext.getString(R.string.settings_save_failed))
            }
            .isSuccess

    fun setGestureHintStyle(style: GestureHintStyle) = launchSettingsWrite {
        settingsRepository.setGestureHintStyle(style)
    }

    fun setGestureHintFingerOffsetDp(value: Float) = launchSettingsWrite {
        settingsRepository.setGestureHintFingerOffsetDp(value)
    }

    fun updateWaveStyle(style: WaveStyle) = launchSettingsWrite {
        settingsRepository.updateWaveStyle(style)
    }

    fun updateCapsuleStyle(style: CapsuleStyle) = launchSettingsWrite {
        settingsRepository.updateCapsuleStyle(style)
    }

    fun updateBubbleStyle(style: BubbleStyle) = launchSettingsWrite {
        settingsRepository.updateBubbleStyle(style)
    }

    fun setCornerGestureEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureEnabled(enabled)
    }

    fun setCornerGestureLeftEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureLeftEnabled(enabled)
    }

    fun setCornerGestureRightEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureRightEnabled(enabled)
    }

    fun setCornerGestureVerticalEdgeWidthDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureVerticalEdgeWidthDp(value)
    }

    fun setCornerGestureVerticalEdgeHeightDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureVerticalEdgeHeightDp(value)
    }

    fun setCornerGestureHorizontalEdgeWidthDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureHorizontalEdgeWidthDp(value)
    }

    fun setCornerGestureHorizontalEdgeHeightDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureHorizontalEdgeHeightDp(value)
    }

    fun setCornerGestureTriggerSlopDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureTriggerSlopDp(value)
    }

    fun setCornerGestureHideInLandscape(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureHideInLandscape(enabled)
    }

    fun setCornerGestureLandscapePreventFalseTouch(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureLandscapePreventFalseTouch(enabled)
    }

    fun setCornerGestureOverrideSystemNav(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureOverrideSystemNav(enabled)
    }

    fun setCornerGestureOuterDiameterDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureOuterDiameterDp(value)
    }

    fun setCornerGestureInnerDiameterDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureInnerDiameterDp(value)
    }

    fun setCornerGestureBubbleSizeDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureBubbleSizeDp(value)
    }

    fun setCornerGestureCancelOutsideWheel(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureCancelOutsideWheel(enabled)
    }

    fun setCornerGestureProgressiveLayers(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureProgressiveLayers(enabled)
    }

    fun setCornerGestureSlotHaptic(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureSlotHaptic(enabled)
    }

    fun setCornerGestureUnifiedSlots(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureUnifiedSlots(enabled)
    }

    fun setCornerGestureInnerZoneAction(action: GestureAction) = launchSettingsWrite {
        settingsRepository.setCornerGestureInnerZoneAction(action)
    }

    fun setCornerGestureLeftSlotAction(index: Int, action: GestureAction) = launchSettingsWrite {
        settingsRepository.setCornerGestureLeftSlotAction(index, action)
    }

    fun setCornerGestureRightSlotAction(index: Int, action: GestureAction) = launchSettingsWrite {
        settingsRepository.setCornerGestureRightSlotAction(index, action)
    }
}
