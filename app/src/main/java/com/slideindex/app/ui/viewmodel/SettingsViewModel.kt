package com.slideindex.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.shake.ShakeGestureType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class SettingsViewModel(
    protected val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings(),
        )

    protected fun launchSettingsUpdate(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

class ShakeHubViewModel(
    settingsRepository: SettingsRepository,
) : SettingsViewModel(settingsRepository) {
    fun setEnabled(enabled: Boolean) = launchSettingsUpdate {
        settingsRepository.setShakeGesturesEnabled(enabled)
    }

    fun setBasicAction(type: ShakeGestureType, action: GestureAction) = launchSettingsUpdate {
        settingsRepository.setShakeGestureAction(type, action)
    }

    fun setLockScreenShakeEnabled(enabled: Boolean) = launchSettingsUpdate {
        settingsRepository.setLockScreenShakeEnabled(enabled)
    }

    fun setIndependentAppShakeEnabled(enabled: Boolean) = launchSettingsUpdate {
        settingsRepository.setIndependentAppShakeEnabled(enabled)
    }

    fun setGlobalSensitivity(value: Float) = launchSettingsUpdate {
        settingsRepository.setShakeGlobalSensitivity(value)
    }

    fun setIndependentSensitivityEnabled(enabled: Boolean) = launchSettingsUpdate {
        settingsRepository.setShakeIndependentSensitivityEnabled(enabled)
    }

    fun setAnimationFeedbackEnabled(enabled: Boolean) = launchSettingsUpdate {
        settingsRepository.setShakeAnimationFeedbackEnabled(enabled)
    }

    fun setVibrationFeedbackEnabled(enabled: Boolean) = launchSettingsUpdate {
        settingsRepository.setShakeVibrationFeedbackEnabled(enabled)
    }

    fun setAnimationColor(color: Int) = launchSettingsUpdate {
        settingsRepository.setShakeAnimationColor(color)
    }

    fun setDisableInLandscape(enabled: Boolean) = launchSettingsUpdate {
        settingsRepository.setShakeDisableInLandscape(enabled)
    }
}
