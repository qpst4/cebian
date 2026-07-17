package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.settings.FloatingPointerEdgeSide
import com.slideindex.app.settings.FloatingPointerTrailType
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.feedback.UserMessageBus
import com.slideindex.app.widget.WidgetPanelPage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class ExtensionSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    fun setQuickLauncherItems(items: List<QuickLauncherItem>) = launchSettingsWrite {
        settingsRepository.setQuickLauncherItems(items)
    }

    fun setQuickLauncherColumnsPerPage(value: Int) = launchSettingsWrite {
        settingsRepository.setQuickLauncherColumnsPerPage(value)
    }

    fun setQuickLauncherRowsPerPage(value: Int) = launchSettingsWrite {
        settingsRepository.setQuickLauncherRowsPerPage(value)
    }

    fun setShellCommands(items: List<ShellCommand>) = launchSettingsWrite {
        settingsRepository.setShellCommands(items)
    }

    fun setWidgetPanelPages(pages: List<WidgetPanelPage>) = launchSettingsWrite {
        settingsRepository.setWidgetPanelPages(pages)
    }

    fun setWidgetPanelBlurEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setWidgetPanelBlurEnabled(enabled)
    }

    fun setWidgetPanelWidthFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setWidgetPanelWidthFraction(fraction)
    }

    fun setFloatingPointerSensitivityFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerSensitivityFraction(fraction)
    }

    fun setFloatingPointerPointerDiameterPx(size: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerPointerDiameterPx(size)
    }

    fun setFloatingPointerRingThicknessPx(thickness: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRingThicknessPx(thickness)
    }

    fun setFloatingPointerDotDiameterPx(diameter: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerDotDiameterPx(diameter)
    }

    fun setFloatingPointerRingColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRingColor(color)
    }

    fun setFloatingPointerFillColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerFillColor(color)
    }

    fun setFloatingPointerDotColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerDotColor(color)
    }

    fun setFloatingPointerClickVisualFeedbackEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerClickVisualFeedbackEnabled(enabled)
    }

    fun setFloatingPointerClickHapticEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerClickHapticEnabled(enabled)
    }

    fun setFloatingPointerRippleColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRippleColor(color)
    }

    fun setFloatingPointerRippleSizeDp(size: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRippleSizeDp(size)
    }

    fun setFloatingPointerRippleDurationMs(duration: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRippleDurationMs(duration)
    }

    fun setFloatingPointerTrailType(type: FloatingPointerTrailType) = launchSettingsWrite {
        settingsRepository.setFloatingPointerTrailType(type)
    }

    fun setFloatingPointerTrailDurationMs(duration: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerTrailDurationMs(duration)
    }

    fun setFloatingPointerTrailColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerTrailColor(color)
    }

    fun setFloatingPointerHideWhenJoystickReleased(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerHideWhenJoystickReleased(enabled)
    }

    fun setFloatingPointerDesignId(designId: String) = launchSettingsWrite {
        settingsRepository.setFloatingPointerDesignId(designId)
    }

    fun resetFloatingPointerVisualDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerVisualDefaults()
    }

    fun setFloatingPointerJoystickDiameterPx(size: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickDiameterPx(size)
    }

    fun setFloatingPointerJoystickInnerColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickInnerColor(color)
    }

    fun setFloatingPointerJoystickOuterColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickOuterColor(color)
    }

    fun setFloatingPointerJoystickGradientRadiusFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickGradientRadiusFraction(fraction)
    }

    fun setFloatingPointerHideOnOutsideClick(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerHideOnOutsideClick(enabled)
    }

    fun setFloatingPointerHideOnQuickSwipe(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerHideOnQuickSwipe(enabled)
    }

    fun setFloatingPointerHideWhenIdle(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerHideWhenIdle(enabled)
    }

    fun setFloatingPointerClickDistanceThresholdDp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerClickDistanceThresholdDp(value)
    }

    fun setFloatingPointerIdleHideDelayMs(delayMs: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerIdleHideDelayMs(delayMs)
    }

    fun setFloatingPointerJoystickLongPressAction(action: GestureAction) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickLongPressAction(action)
    }

    fun resetFloatingPointerJoystickVisualDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerJoystickVisualDefaults()
    }

    fun resetFloatingPointerJoystickBehaviorDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerJoystickBehaviorDefaults()
    }

    fun setFloatingPointerRadialAlwaysVisible(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialAlwaysVisible(enabled)
    }

    fun setFloatingPointerRadialLongPressMs(ms: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialLongPressMs(ms)
    }

    fun setFloatingPointerRadialSlotAction(index: Int, action: GestureAction) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialSlotAction(index, action)
    }

    fun setFloatingPointerRadialOuterDiameterPx(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialOuterDiameterPx(value)
    }

    fun setFloatingPointerRadialInnerDiameterPx(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialInnerDiameterPx(value)
    }

    fun setFloatingPointerRadialOuterColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialOuterColor(color)
    }

    fun setFloatingPointerRadialInnerColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialInnerColor(color)
    }

    fun setFloatingPointerRadialDividerThicknessPx(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialDividerThicknessPx(value)
    }

    fun setFloatingPointerRadialDividerColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialDividerColor(color)
    }

    fun setFloatingPointerRadialIconSizeFraction(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialIconSizeFraction(value)
    }

    fun setFloatingPointerRadialIconColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialIconColor(color)
    }

    fun resetFloatingPointerRadialDesignDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerRadialDesignDefaults()
    }

    fun setFloatingPointerEdgeThresholdDp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgeThresholdDp(value)
    }

    fun setFloatingPointerEdgePreviewSensitivity(value: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgePreviewSensitivity(value)
    }

    fun setFloatingPointerEdgePreviewGlowSize(value: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgePreviewGlowSize(value)
    }

    fun setFloatingPointerEdgePreviewShowIcon(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgePreviewShowIcon(enabled)
    }

    fun setFloatingPointerEdgeVisualColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgeVisualColor(color)
    }

    fun setFloatingPointerEdgeBarEnabled(side: FloatingPointerEdgeSide, enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgeBarEnabled(side, enabled)
    }

    fun setFloatingPointerEdgeBarSlotAction(
        side: FloatingPointerEdgeSide,
        slotIndex: Int,
        action: GestureAction,
    ) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgeBarSlotAction(side, slotIndex, action)
    }

    fun addFloatingPointerEdgeBarSlot(side: FloatingPointerEdgeSide) = launchSettingsWrite {
        settingsRepository.addFloatingPointerEdgeBarSlot(side)
    }

    fun removeFloatingPointerEdgeBarSlot(side: FloatingPointerEdgeSide, slotIndex: Int) = launchSettingsWrite {
        settingsRepository.removeFloatingPointerEdgeBarSlot(side, slotIndex)
    }

    fun resetFloatingPointerEdgeDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerEdgeDefaults()
    }

    fun setFloatBallEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallEnabled(enabled)
    }

    fun setFloatBallSizeDp(sizeDp: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallSizeDp(sizeDp)
    }

    fun setFloatBallOpacity(opacity: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallOpacity(opacity)
    }

    fun setFloatBallOcrFallbackEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallOcrFallbackEnabled(enabled)
    }

    fun setFloatBallPointerSpeedFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPointerSpeedFraction(fraction)
    }

    fun setFloatBallPositionMode(mode: FloatBallPositionMode) = launchSettingsWrite {
        settingsRepository.setFloatBallPositionMode(mode)
    }

    fun setFloatBallPositionYFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPositionYFraction(fraction)
    }

    fun setFloatBallPositionXFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPositionXFraction(fraction)
    }

    fun setFloatBallLineHeightFraction(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallLineHeightFraction(value)
    }

    fun setFloatBallLineWidthFraction(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallLineWidthFraction(value)
    }

    fun setFloatBallLineOpacity(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallLineOpacity(value)
    }

    fun setFloatBallPointerSlopDp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPointerSlopDp(value)
    }

    fun setFloatBallPickOffsetDp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPickOffsetDp(value)
    }

    fun setFloatBallPickTextSizeSp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPickTextSizeSp(value)
    }

    fun setFloatBallPickBottomTransitionFraction(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPickBottomTransitionFraction(value)
    }

    fun setShareImageOcrHistoryEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setShareImageOcrHistoryEnabled(enabled)
    }
}
