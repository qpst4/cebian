package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.settings.FloatingPointerTrailType
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

    fun setFloatingPointerJoystickAreaZoomFraction(zoom: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickAreaZoomFraction(zoom)
    }

    fun setFloatingPointerJoystickAreaWidthPx(width: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickAreaWidthPx(width)
    }

    fun setFloatingPointerJoystickAreaHeightPx(height: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickAreaHeightPx(height)
    }

    fun setFloatingPointerMatchJoystickToScreenAspect(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerMatchJoystickToScreenAspect(enabled)
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

    fun setFloatingPointerIdleHideDelayMs(delayMs: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerIdleHideDelayMs(delayMs)
    }

    fun resetFloatingPointerJoystickVisualDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerJoystickVisualDefaults()
    }

    fun resetFloatingPointerJoystickBehaviorDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerJoystickBehaviorDefaults()
    }

    fun setFloatingPointerRadialMenuEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialMenuEnabled(enabled)
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
}
