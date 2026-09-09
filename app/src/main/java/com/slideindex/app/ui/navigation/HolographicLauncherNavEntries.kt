package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.R
import com.slideindex.app.ui.HiddenAppsScreen
import com.slideindex.app.ui.HolographicLauncherSettingsScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel

fun NavEntryBuilder.holographicLauncherNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.HolographicLauncherSettings> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val holographicSettings = gestureSettings.holographicLauncher
        HolographicLauncherSettingsScreen(
            settings = holographicSettings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onTimeoutSecondsChange = viewModel::setHolographicLauncherTimeoutSeconds,
            onRotationSensitivityChange = viewModel::setHolographicRotationSensitivity,
            onHapticLevelChange = viewModel::setHolographicHapticLevel,
            onBackgroundStyleChange = viewModel::setHolographicBackgroundStyle,
            onBlurDpChange = viewModel::setHolographicBlurDp,
            onDimPercentChange = viewModel::setHolographicDimPercent,
            onOpenHiddenApps = { ctx.navigate(AppNavKey.HolographicLauncherHiddenApps) },
        )
    }

    hiltEntry<AppNavKey.HolographicLauncherHiddenApps> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        HiddenAppsScreen(
            hiddenPackages = gestureSettings.holographicLauncher.hiddenAppPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.HolographicLauncherSettings) },
            onHideApp = viewModel::addHolographicHiddenApp,
            onUnhideApp = viewModel::removeHolographicHiddenApp,
            titleRes = R.string.hidden_apps_title,
            descriptionRes = R.string.holographic_hidden_apps_desc,
        )
    }
}
