package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import com.slideindex.app.R
import com.slideindex.app.shake.ShakeGestureSettings
import com.slideindex.app.ui.ShakeActionSetSettingsScreen
import com.slideindex.app.ui.ShakeGestureBlacklistScreen
import com.slideindex.app.ui.ShakeGesturesScreen
import com.slideindex.app.ui.ShakeIndependentAppSettingsScreen
import com.slideindex.app.ui.ShakeIndependentSensitivityScreen
import com.slideindex.app.ui.viewmodel.ShakeHubViewModel

fun EntryProviderScope<AppNavKey>.shakeNavEntries(ctx: MainNavContext) {
    entry<AppNavKey.ShakeGestures> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        ShakeGesturesScreen(
            settings = settings.shakeGestureSettings,
            bottomContentPadding = ctx.rootBottomContentPadding,
            onEnabledChange = { enabled -> viewModel.setEnabled(enabled) },
            onBasicActionChange = { type, action -> viewModel.setBasicAction(type, action) },
            onLockScreenShakeEnabledChange = { enabled -> viewModel.setLockScreenShakeEnabled(enabled) },
            onIndependentAppShakeEnabledChange = { enabled ->
                viewModel.setIndependentAppShakeEnabled(enabled)
            },
            onGlobalSensitivityChange = { value -> viewModel.setGlobalSensitivity(value) },
            onIndependentSensitivityEnabledChange = { enabled ->
                viewModel.setIndependentSensitivityEnabled(enabled)
            },
            onOpenIndependentSensitivity = { ctx.navigate(AppNavKey.ShakeIndependentSensitivity) },
            onAnimationFeedbackEnabledChange = { enabled -> viewModel.setAnimationFeedbackEnabled(enabled) },
            onVibrationFeedbackEnabledChange = { enabled -> viewModel.setVibrationFeedbackEnabled(enabled) },
            onAnimationColorChange = { color -> viewModel.setAnimationColor(color) },
            onDisableInLandscapeChange = { enabled -> viewModel.setDisableInLandscape(enabled) },
            onOpenLockScreenShakeSettings = { ctx.navigate(AppNavKey.ShakeLockScreenSettings) },
            onOpenIndependentAppShakeSettings = { ctx.navigate(AppNavKey.ShakeIndependentAppSettings) },
            onOpenAppBlacklist = { ctx.navigate(AppNavKey.ShakeGestureBlacklist) },
        )
    }

    entry<AppNavKey.ShakeGestureBlacklist> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        ShakeGestureBlacklistScreen(
            blacklistedPackages = settings.shakeGestureSettings.blacklistedPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestures) },
            onBlacklistApp = { packageName -> viewModel.addShakeBlacklistedApp(packageName) },
            onRemoveBlacklistedApp = { packageName -> viewModel.removeShakeBlacklistedApp(packageName) },
        )
    }

    entry<AppNavKey.ShakeLockScreenSettings> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        ShakeActionSetSettingsScreen(
            title = stringResource(R.string.shake_gestures_lock_screen),
            subtitle = stringResource(R.string.shake_gestures_lock_screen_settings_desc),
            actions = settings.shakeGestureSettings.lockScreenActions,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestures) },
            onActionChange = { type, action -> viewModel.setLockScreenShakeAction(type, action) },
        )
    }

    entry<AppNavKey.ShakeIndependentSensitivity> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        ShakeIndependentSensitivityScreen(
            globalSensitivity = settings.shakeGestureSettings.globalSensitivity,
            perDirectionSensitivity = settings.shakeGestureSettings.perDirectionSensitivity,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestures) },
            onSensitivityChange = { type, value -> viewModel.setShakeDirectionSensitivity(type, value) },
        )
    }

    entry<AppNavKey.ShakeIndependentAppSettings> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        ShakeIndependentAppSettingsScreen(
            perAppActions = settings.shakeGestureSettings.perAppActions,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestures) },
            onOpenAppConfig = { packageName ->
                viewModel.addPerAppShakeConfig(packageName)
                ctx.navigate(AppNavKey.ShakePerAppActions(packageName))
            },
            onRemoveAppConfig = { packageName -> viewModel.removePerAppShakeConfig(packageName) },
        )
    }

    entry<AppNavKey.ShakePerAppActions> { key ->
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val packageName = key.packageName
        val appLabel = remember(packageName) {
            runCatching {
                ctx.activity.packageManager.getApplicationLabel(
                    ctx.activity.packageManager.getApplicationInfo(packageName, 0),
                ).toString()
            }.getOrDefault(packageName)
        }
        ShakeActionSetSettingsScreen(
            title = stringResource(
                R.string.shake_gestures_per_app_actions_title,
                appLabel,
            ),
            subtitle = stringResource(R.string.shake_gestures_independent_app_desc),
            actions = settings.shakeGestureSettings.perAppActions[packageName]
                ?: ShakeGestureSettings.defaultBasicActions(),
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeIndependentAppSettings) },
            onActionChange = { type, action ->
                viewModel.setPerAppShakeAction(packageName, type, action)
            },
        )
    }
}
