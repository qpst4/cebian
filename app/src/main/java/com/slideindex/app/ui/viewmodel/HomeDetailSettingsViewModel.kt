package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerDesignPreset
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.gesture.GestureAngleConfig
import com.slideindex.app.settings.BubbleStyle
import com.slideindex.app.settings.CapsuleStyle
import com.slideindex.app.settings.GestureHintStyle
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.settings.WaveStyle
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class HomeDetailSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
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

    fun setTriggerVerticalRange(side: PanelSide, handleId: String, top: Float, bottom: Float) =
        launchSettingsWrite {
            settingsRepository.setTriggerVerticalRange(side, handleId, top, bottom)
        }

    fun setTriggerAlignOppositeSide(handleId: String, sourceSide: PanelSide, enabled: Boolean) =
        launchSettingsWrite {
            settingsRepository.setTriggerAlignOppositeSide(
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
        launchSettingsWrite {
            settingsRepository.setTriggerHandleDesign(side, handleId, design)
        }

    fun applyTriggerDesignPreset(side: PanelSide, handleId: String, preset: TriggerDesignPreset) =
        launchSettingsWrite {
            settingsRepository.applyTriggerDesignPreset(side, handleId, preset)
        }

    fun setGestureAngleConfig(config: GestureAngleConfig) = launchSettingsWrite {
        settingsRepository.setGestureAngleConfig(config)
    }

    fun setGestureHintStyle(style: GestureHintStyle) = launchSettingsWrite {
        settingsRepository.setGestureHintStyle(style)
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
}
