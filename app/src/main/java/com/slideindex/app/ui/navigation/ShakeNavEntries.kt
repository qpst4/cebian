package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.shake.ShakeGestureSettings
import com.slideindex.app.shake.ShakeGestureType
import com.slideindex.app.ui.GestureActionPickerScreen
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.ShakeActionSetSettingsScreen
import com.slideindex.app.ui.ShakeGestureBlacklistScreen
import com.slideindex.app.ui.ShakeGesturesScreen
import com.slideindex.app.ui.ShakeIndependentAppSettingsScreen
import com.slideindex.app.ui.ShakeIndependentSensitivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.viewmodel.ShakeHubViewModel

fun NavEntryBuilder.shakeNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.ShakeGestures> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        ShakeGesturesScreen(
            settings = shakeSettings.shakeGestureSettings,
            faceDownSettings = shakeSettings.faceDownGestureSettings,
            bottomContentPadding = ctx.rootBottomContentPadding,
            bottomNavReselectCount = ctx.bottomNavReselectCount,
            onEnabledChange = { enabled -> viewModel.setEnabled(enabled) },
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
            onFaceDownEnabledChange = { enabled -> viewModel.setFaceDownEnabled(enabled) },
            onFaceDownHoldDurationChange = { ms -> viewModel.setFaceDownHoldDurationMs(ms) },
            onFaceDownRequireProximityChange = { enabled -> viewModel.setFaceDownRequireProximity(enabled) },
            onFaceDownDisableInLandscapeChange = { enabled -> viewModel.setFaceDownDisableInLandscape(enabled) },
            onFaceDownVibrationFeedbackChange = { enabled -> viewModel.setFaceDownVibrationFeedbackEnabled(enabled) },
            onFaceDownAudioFeedbackChange = { enabled -> viewModel.setFaceDownAudioFeedbackEnabled(enabled) },
            onFaceDownAudioFeedbackVolumeChange = { volume -> viewModel.setFaceDownAudioFeedbackVolume(volume) },
            onOpenLockScreenShakeSettings = { ctx.navigate(AppNavKey.ShakeLockScreenSettings) },
            onOpenIndependentAppShakeSettings = { ctx.navigate(AppNavKey.ShakeIndependentAppSettings) },
            onOpenAppBlacklist = { ctx.navigate(AppNavKey.ShakeGestureBlacklist) },
            onOpenBasicActionPick = { type ->
                ctx.navigate(
                    AppNavKey.ShakeGestureActionPick(
                        target = ShakeActionPickTarget.BASIC,
                        gestureTypeId = type.id,
                    ),
                )
            },
            onOpenFaceDownActionPick = {
                ctx.navigate(
                    AppNavKey.ShakeGestureActionPick(
                        target = ShakeActionPickTarget.FACE_DOWN,
                        gestureTypeId = ShakeGestureType.LEFT_FLIP.id,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.ShakeGestureActionPick> { key ->
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        val gestureType = ShakeGestureType.fromId(key.gestureTypeId) ?: ShakeGestureType.LEFT_FLIP
        val returnKey = key.target.returnNavKey(key.packageName)
        val trigger = when (key.target) {
            ShakeActionPickTarget.BASIC,
            ShakeActionPickTarget.FACE_DOWN,
            -> GestureTriggerType.SHORT_SINGLE_TAP
            ShakeActionPickTarget.LOCK_SCREEN,
            ShakeActionPickTarget.PER_APP,
            -> GestureTriggerType.SHORT_SWIPE_IN
        }
        val currentAction = when (key.target) {
            ShakeActionPickTarget.BASIC ->
                shakeSettings.shakeGestureSettings.actionFor(gestureType)
            ShakeActionPickTarget.FACE_DOWN ->
                shakeSettings.faceDownGestureSettings.action
            ShakeActionPickTarget.LOCK_SCREEN ->
                shakeSettings.shakeGestureSettings.lockScreenActions[gestureType] ?: GestureAction.None
            ShakeActionPickTarget.PER_APP ->
                shakeSettings.shakeGestureSettings.perAppActions[key.packageName]
                    ?.get(gestureType) ?: GestureAction.None
        }
        GestureActionPickerScreen(
            trigger = trigger,
            current = currentAction,
            onDismiss = { ctx.navigateBackTo(returnKey) },
            onSelect = { action ->
                applyShakePickedAction(viewModel, key.target, gestureType, key.packageName, action)
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.ShakeGestureActionShellCommand> { key ->
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val extensionViewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by extensionViewModel.overlaySettings.collectAsStateWithLifecycle()
        val gestureType = ShakeGestureType.fromId(key.gestureTypeId) ?: ShakeGestureType.LEFT_FLIP
        val returnKey = key.target.returnNavKey(key.packageName)
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = overlaySettings.toMinimalAppSettings().shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                applyShakePickedAction(
                    viewModel,
                    key.target,
                    gestureType,
                    key.packageName,
                    GestureAction.ExecuteShellCommand(command),
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.ShakeGestureBlacklist> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        ShakeGestureBlacklistScreen(
            blacklistedPackages = shakeSettings.shakeGestureSettings.blacklistedPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestures) },
            onOpenAddApp = { ctx.navigate(AppNavKey.ShakeGestureBlacklistPick) },
            onRemoveBlacklistedApp = { packageName -> viewModel.removeShakeBlacklistedApp(packageName) },
        )
    }

    hiltEntry<AppNavKey.ShakeGestureBlacklistPick> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        ActivityShortcutPickAppScreen(
            titleResId = R.string.shake_gestures_blacklist_section_add,
            excludePackageNames = shakeSettings.shakeGestureSettings.blacklistedPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestureBlacklist) },
            onSelectApp = { app ->
                viewModel.addShakeBlacklistedApp(app.packageName)
                ctx.navigateBackTo(AppNavKey.ShakeGestureBlacklist)
            },
        )
    }

    hiltEntry<AppNavKey.ShakeLockScreenSettings> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        ShakeActionSetSettingsScreen(
            title = stringResource(R.string.shake_gestures_lock_screen),
            subtitle = stringResource(R.string.shake_gestures_lock_screen_settings_desc),
            actions = shakeSettings.shakeGestureSettings.lockScreenActions,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestures) },
            onOpenActionPick = { type ->
                ctx.navigate(
                    AppNavKey.ShakeGestureActionPick(
                        target = ShakeActionPickTarget.LOCK_SCREEN,
                        gestureTypeId = type.id,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.ShakeIndependentSensitivity> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        ShakeIndependentSensitivityScreen(
            globalSensitivity = shakeSettings.shakeGestureSettings.globalSensitivity,
            perDirectionSensitivity = shakeSettings.shakeGestureSettings.perDirectionSensitivity,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestures) },
            onSensitivityChange = { type, value -> viewModel.setShakeDirectionSensitivity(type, value) },
        )
    }

    hiltEntry<AppNavKey.ShakeIndependentAppSettings> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        ShakeIndependentAppSettingsScreen(
            perAppActions = shakeSettings.shakeGestureSettings.perAppActions,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestures) },
            onOpenAddApp = { ctx.navigate(AppNavKey.ShakeIndependentAppPick) },
            onOpenConfiguredApp = { packageName ->
                ctx.navigate(AppNavKey.ShakePerAppActions(packageName))
            },
            onRemoveAppConfig = { packageName -> viewModel.removePerAppShakeConfig(packageName) },
        )
    }

    hiltEntry<AppNavKey.ShakeIndependentAppPick> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        ActivityShortcutPickAppScreen(
            titleResId = R.string.shake_gestures_per_app_add,
            excludePackageNames = shakeSettings.shakeGestureSettings.perAppActions.keys,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeIndependentAppSettings) },
            onSelectApp = { app ->
                viewModel.addPerAppShakeConfig(app.packageName)
                ctx.navigate(AppNavKey.ShakePerAppActions(app.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.ShakePerAppActions> { key ->
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
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
            actions = shakeSettings.shakeGestureSettings.perAppActions[packageName]
                ?: ShakeGestureSettings.defaultBasicActions(),
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeIndependentAppSettings) },
            onOpenActionPick = { type ->
                ctx.navigate(
                    AppNavKey.ShakeGestureActionPick(
                        target = ShakeActionPickTarget.PER_APP,
                        gestureTypeId = type.id,
                        packageName = packageName,
                    ),
                )
            },
        )
    }
}

private fun applyShakePickedAction(
    viewModel: ShakeHubViewModel,
    target: ShakeActionPickTarget,
    gestureType: ShakeGestureType,
    packageName: String,
    action: GestureAction,
) {
    when (target) {
        ShakeActionPickTarget.BASIC -> viewModel.setBasicAction(gestureType, action)
        ShakeActionPickTarget.FACE_DOWN -> viewModel.setFaceDownAction(action)
        ShakeActionPickTarget.LOCK_SCREEN -> viewModel.setLockScreenShakeAction(gestureType, action)
        ShakeActionPickTarget.PER_APP -> viewModel.setPerAppShakeAction(packageName, gestureType, action)
    }
}
