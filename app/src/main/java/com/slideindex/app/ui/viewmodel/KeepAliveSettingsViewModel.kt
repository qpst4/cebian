package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

interface KeepAliveScreenEffects {
    fun applyHideFromRecents(enabled: Boolean)
    fun onAccessibilityKeepAliveEnabled()
}

@HiltViewModel(assistedFactory = KeepAliveSettingsViewModel.Factory::class)
class KeepAliveSettingsViewModel @AssistedInject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
    @Assisted private val effects: KeepAliveScreenEffects,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    fun setHideFromRecents(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHideFromRecents(enabled).also { result ->
            if (result.isSuccess) {
                effects.applyHideFromRecents(enabled)
            }
        }
    }

    fun setAccessibilityKeepAliveEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setAccessibilityKeepAliveEnabled(enabled).also { result ->
            if (result.isSuccess && enabled) {
                effects.onAccessibilityKeepAliveEnabled()
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(effects: KeepAliveScreenEffects): KeepAliveSettingsViewModel
    }
}
