package com.slideindex.app.ui.viewmodel

import android.content.Context
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.slideindex.app.settings.BottomNavBlurDefaults
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.settings.ThemePaletteStyle
import com.slideindex.app.ui.feedback.UserMessageBus

interface HomeScreenEffects {
    fun refreshServiceState()
    fun requestNotificationPermission()
    fun requestShizuku()
    fun openAccessibilitySettings()
    fun previewHaptic(enabled: Boolean = true, strengthLevel: Int? = null)
}

@HiltViewModel(assistedFactory = HomeViewModel.Factory::class)
class HomeViewModel @AssistedInject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
    @Assisted private val effects: HomeScreenEffects,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    fun setServiceEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setServiceEnabled(enabled).also { result ->
            if (result.isSuccess) {
                effects.refreshServiceState()
            }
        }
    }

    fun setHapticEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHapticEnabled(enabled).also { result ->
            if (result.isSuccess && enabled) {
                effects.previewHaptic()
            }
        }
    }

    fun setHapticStrength(level: Int) = launchSettingsWrite {
        settingsRepository.setHapticStrengthLevel(level).also { result ->
            if (result.isSuccess) {
                effects.previewHaptic(strengthLevel = level)
            }
        }
    }

    fun setGestureHintEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setGestureHintEnabled(enabled)
    }

    fun setHideTriggerInLandscape(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHideTriggerInLandscape(enabled)
    }

    fun setHideTriggerOnLockScreen(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHideTriggerOnLockScreen(enabled)
    }

    fun setHideTriggerOnLauncher(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHideTriggerOnLauncher(enabled)
    }

    fun setDynamicColorEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setDynamicColorEnabled(enabled)
    }

    fun setThemeColor(color: Int) = launchSettingsWrite {
        settingsRepository.setThemeColor(color)
    }

    fun setThemePaletteStyle(style: ThemePaletteStyle) = launchSettingsWrite {
        settingsRepository.setThemePaletteStyle(style)
    }

    fun setBottomNavGlassEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setBottomNavGlassEnabled(enabled)
    }

    fun setBottomNavBlurRadiusDp(value: Float) = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            settings.copy(
                bottomNavBlurRadiusDp = value.coerceIn(
                    BottomNavBlurDefaults.MIN_RADIUS_DP,
                    BottomNavBlurDefaults.MAX_RADIUS_DP,
                ),
            )
        },
        block = { settingsRepository.setBottomNavBlurRadiusDp(value) },
    )

    fun requestNotificationPermission() = effects.requestNotificationPermission()

    fun requestShizuku() = effects.requestShizuku()

    fun openAccessibilitySettings() = effects.openAccessibilitySettings()

    @AssistedFactory
    interface Factory {
        fun create(effects: HomeScreenEffects): HomeViewModel
    }
}
