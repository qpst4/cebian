package com.slideindex.app.ui.viewmodel

import com.slideindex.app.settings.SettingsRepository

interface HomeScreenEffects {
    fun refreshServiceState()
    fun requestNotificationPermission()
    fun requestShizuku()
    fun openAccessibilitySettings()
    fun previewHaptic(enabled: Boolean = true, strengthLevel: Int? = null)
}

class HomeViewModel(
    settingsRepository: SettingsRepository,
    private val effects: HomeScreenEffects,
) : SettingsViewModel(settingsRepository) {
    fun setServiceEnabled(enabled: Boolean) = launchSettingsUpdate {
        settingsRepository.setServiceEnabled(enabled)
        effects.refreshServiceState()
    }

    fun setHapticEnabled(enabled: Boolean) = launchSettingsUpdate {
        settingsRepository.setHapticEnabled(enabled)
        if (enabled) {
            effects.previewHaptic()
        }
    }

    fun setHapticStrength(level: Int) = launchSettingsUpdate {
        settingsRepository.setHapticStrengthLevel(level)
        effects.previewHaptic(strengthLevel = level)
    }

    fun setGestureHintEnabled(enabled: Boolean) = launchSettingsUpdate {
        settingsRepository.setGestureHintEnabled(enabled)
    }

    fun setHideTriggerInLandscape(enabled: Boolean) = launchSettingsUpdate {
        settingsRepository.setHideTriggerInLandscape(enabled)
    }

    fun setHideTriggerOnLockScreen(enabled: Boolean) = launchSettingsUpdate {
        settingsRepository.setHideTriggerOnLockScreen(enabled)
    }

    fun setHideTriggerOnLauncher(enabled: Boolean) = launchSettingsUpdate {
        settingsRepository.setHideTriggerOnLauncher(enabled)
    }

    fun setDynamicColorEnabled(enabled: Boolean) = launchSettingsUpdate {
        settingsRepository.setDynamicColorEnabled(enabled)
    }

    fun setThemeColor(color: Int) = launchSettingsUpdate {
        settingsRepository.setThemeColor(color)
    }

    fun requestNotificationPermission() = effects.requestNotificationPermission()

    fun requestShizuku() = effects.requestShizuku()

    fun openAccessibilitySettings() = effects.openAccessibilitySettings()
}
