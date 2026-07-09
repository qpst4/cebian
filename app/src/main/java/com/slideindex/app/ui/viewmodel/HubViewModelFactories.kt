package com.slideindex.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.slideindex.app.SlideIndexApp
import com.slideindex.app.ui.navigation.MainNavContext

fun homeViewModelFactory(
    app: SlideIndexApp,
    effects: HomeScreenEffects,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        HomeViewModel(
            settingsRepository = app.settingsRepository,
            effects = effects,
        )
    }
}

fun shakeHubViewModelFactory(app: SlideIndexApp): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        ShakeHubViewModel(settingsRepository = app.settingsRepository)
    }
}

fun notificationHubViewModelFactory(app: SlideIndexApp): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        NotificationHubViewModel(
            settingsRepository = app.settingsRepository,
            notificationHistoryRepository = app.notificationHistoryRepository,
            notificationFilterRepository = app.notificationFilterRepository,
        )
    }
}

fun extensionHubViewModelFactory(app: SlideIndexApp): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        ExtensionHubViewModel(settingsRepository = app.settingsRepository)
    }
}

class MainNavHomeEffects(
    private val ctx: MainNavContext,
) : HomeScreenEffects {
    override fun refreshServiceState() = ctx.refreshServiceState()

    override fun requestNotificationPermission() = ctx.requestNotificationPermission()

    override fun requestShizuku() = ctx.requestShizuku()

    override fun openAccessibilitySettings() = ctx.openAccessibilitySettings()

    override fun previewHaptic(enabled: Boolean, strengthLevel: Int?) {
        ctx.previewHaptic(enabled, strengthLevel)
    }
}
