package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.shake.ShakeGestureType
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class SettingsViewModel(
    protected val settingsRepository: SettingsRepository,
    protected val userMessageBus: UserMessageBus,
    protected val appContext: Context,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings(),
        )

    protected fun launchSettingsWrite(
        @StringRes failureMessageRes: Int = R.string.settings_save_failed,
        block: suspend () -> Result<Unit>,
    ) = launchRepositoryWrite(failureMessageRes, block)

    protected fun launchRepositoryWrite(
        @StringRes failureMessageRes: Int = R.string.settings_save_failed,
        block: suspend () -> Result<Unit>,
    ) {
        viewModelScope.launch {
            block().onFailure {
                userMessageBus.showError(appContext.getString(failureMessageRes))
            }
        }
    }
}

@HiltViewModel
class ShakeHubViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    fun setEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setShakeGesturesEnabled(enabled)
    }

    fun setBasicAction(type: ShakeGestureType, action: GestureAction) = launchSettingsWrite {
        settingsRepository.setShakeGestureAction(type, action)
    }

    fun setLockScreenShakeEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setLockScreenShakeEnabled(enabled)
    }

    fun setIndependentAppShakeEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setIndependentAppShakeEnabled(enabled)
    }

    fun setGlobalSensitivity(value: Float) = launchSettingsWrite {
        settingsRepository.setShakeGlobalSensitivity(value)
    }

    fun setIndependentSensitivityEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setShakeIndependentSensitivityEnabled(enabled)
    }

    fun setAnimationFeedbackEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setShakeAnimationFeedbackEnabled(enabled)
    }

    fun setVibrationFeedbackEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setShakeVibrationFeedbackEnabled(enabled)
    }

    fun setAnimationColor(color: Int) = launchSettingsWrite {
        settingsRepository.setShakeAnimationColor(color)
    }

    fun setDisableInLandscape(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setShakeDisableInLandscape(enabled)
    }

    fun addShakeBlacklistedApp(packageName: String) = launchSettingsWrite {
        settingsRepository.addShakeBlacklistedApp(packageName)
    }

    fun removeShakeBlacklistedApp(packageName: String) = launchSettingsWrite {
        settingsRepository.removeShakeBlacklistedApp(packageName)
    }

    fun setLockScreenShakeAction(type: ShakeGestureType, action: GestureAction) = launchSettingsWrite {
        settingsRepository.setLockScreenShakeAction(type, action)
    }

    fun setShakeDirectionSensitivity(type: ShakeGestureType, value: Float) = launchSettingsWrite {
        settingsRepository.setShakeDirectionSensitivity(type, value)
    }

    fun addPerAppShakeConfig(packageName: String) = launchSettingsWrite {
        settingsRepository.addPerAppShakeConfig(packageName)
    }

    fun removePerAppShakeConfig(packageName: String) = launchSettingsWrite {
        settingsRepository.removePerAppShakeConfig(packageName)
    }

    fun setPerAppShakeAction(packageName: String, type: ShakeGestureType, action: GestureAction) =
        launchSettingsWrite {
            settingsRepository.setPerAppShakeAction(packageName, type, action)
        }
}
