package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slideindex.app.R
import com.slideindex.app.overlay.SystemWallpaperBlurHelper
import com.slideindex.app.overlay.WallpaperPermissionTrampolineActivity
import com.slideindex.app.settings.HolographicLauncherSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HolographicLauncherSettingsScreen(
    settings: HolographicLauncherSettings,
    onBack: () -> Unit,
    onTimeoutSecondsChange: (Int) -> Unit,
    onRotationSensitivityChange: (Float) -> Unit,
    onHapticLevelChange: (Int) -> Unit,
    onBackgroundStyleChange: (Int) -> Unit,
    onBlurDpChange: (Int) -> Unit,
    onDimPercentChange: (Int) -> Unit,
    onOpenHiddenApps: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var localSettings by remember { mutableStateOf(settings) }
    var wallpaperPermissionGranted by remember {
        mutableStateOf(SystemWallpaperBlurHelper.hasWallpaperAccessPermission(context))
    }
    LaunchedEffect(settings) { localSettings = settings }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                wallpaperPermissionGranted =
                    SystemWallpaperBlurHelper.hasWallpaperAccessPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun ensureWallpaperPermission() {
        WallpaperPermissionTrampolineActivity.launch(context)
    }

    val interactionSectionTitle = stringResource(R.string.holographic_settings_section_interaction)
    val backgroundSectionTitle = stringResource(R.string.holographic_settings_section_background)
    val appsSectionTitle = stringResource(R.string.holographic_settings_section_apps)
    val sensitivityRange = HolographicLauncherSettings.MIN_ROTATION_SENSITIVITY..
        HolographicLauncherSettings.MAX_ROTATION_SENSITIVITY
    val sensitivitySteps = 49
    val hapticSectionTitle = stringResource(R.string.holographic_settings_haptic)
    val hapticLabels = listOf(
        stringResource(R.string.holographic_settings_haptic_off),
        stringResource(R.string.holographic_settings_haptic_light),
        stringResource(R.string.holographic_settings_haptic_medium),
        stringResource(R.string.holographic_settings_haptic_strong),
    )
    val backgroundStyles = listOf(
        HolographicLauncherSettings.BACKGROUND_BLUR,
        HolographicLauncherSettings.BACKGROUND_WALLPAPER_BLUR,
        HolographicLauncherSettings.BACKGROUND_BLACK,
    )
    val blurEnabled = localSettings.backgroundStyle != HolographicLauncherSettings.BACKGROUND_BLACK

    SettingsScreenScaffold(
        title = stringResource(R.string.holographic_launcher_settings_title),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "section-interaction",
            title = interactionSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "holographic-interaction",
            items = buildList {
                add(
                    settingsCardScopeItem("timeout") {
                        SettingsSliderRow(
                            title = stringResource(R.string.holographic_settings_timeout),
                            value = localSettings.timeoutSeconds.toFloat(),
                            valueRange = HolographicLauncherSettings.MIN_TIMEOUT_SECONDS.toFloat()..
                                HolographicLauncherSettings.MAX_TIMEOUT_SECONDS.toFloat(),
                            steps = 59,
                            enabled = true,
                            label = stringResource(
                                R.string.holographic_settings_timeout_value,
                                localSettings.timeoutSeconds,
                            ),
                            onValueChange = { value ->
                                val next = value.roundToInt()
                                localSettings = localSettings.copy(timeoutSeconds = next)
                                onTimeoutSecondsChange(next)
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("sensitivity") {
                        SettingsSliderRow(
                            title = stringResource(R.string.holographic_settings_sensitivity),
                            value = localSettings.rotationSensitivity,
                            valueRange = sensitivityRange,
                            steps = sensitivitySteps,
                            enabled = true,
                            label = stringResource(
                                R.string.holographic_settings_sensitivity_value,
                                (localSettings.rotationSensitivity * 1000).roundToInt(),
                            ),
                            onValueChange = { value ->
                                localSettings = localSettings.copy(rotationSensitivity = value)
                                onRotationSensitivityChange(value)
                            },
                        )
                    },
                )
            },
        )
        settingsLazySmallTitle(
            key = "section-background",
            title = backgroundSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "holographic-background",
            items = buildList {
                add(
                    settingsCardScopeItem("background-style") {
                        SettingDropdownRow(
                            title = stringResource(R.string.holographic_settings_background_style),
                            items = listOf(
                                stringResource(R.string.honeycomb_background_blur),
                                stringResource(R.string.honeycomb_background_wallpaper_blur),
                                stringResource(R.string.honeycomb_background_black),
                            ),
                            selectedIndex = backgroundStyles.indexOf(localSettings.backgroundStyle).coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                val style = backgroundStyles[index]
                                localSettings = localSettings.copy(backgroundStyle = style)
                                onBackgroundStyleChange(style)
                                if (style == HolographicLauncherSettings.BACKGROUND_WALLPAPER_BLUR &&
                                    !SystemWallpaperBlurHelper.hasWallpaperAccessPermission(context)
                                ) {
                                    ensureWallpaperPermission()
                                }
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("wallpaper-permission") {
                        SettingLinkRow(
                            title = stringResource(R.string.wallpaper_blur_permission_title),
                            subtitle = stringResource(
                                if (wallpaperPermissionGranted) {
                                    R.string.wallpaper_blur_permission_granted
                                } else {
                                    R.string.wallpaper_blur_permission_missing
                                },
                            ),
                            enabled = localSettings.backgroundStyle ==
                                HolographicLauncherSettings.BACKGROUND_WALLPAPER_BLUR &&
                                !wallpaperPermissionGranted,
                            onClick = { ensureWallpaperPermission() },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("blur-strength") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_blur_strength),
                            value = localSettings.blurDp.toFloat(),
                            valueRange = HolographicLauncherSettings.MIN_BLUR_DP.toFloat()..
                                HolographicLauncherSettings.MAX_BLUR_DP.toFloat(),
                            steps = 16,
                            enabled = blurEnabled,
                            label = stringResource(R.string.corner_gesture_zone_dp_value, localSettings.blurDp),
                            onValueChange = { value ->
                                val next = value.roundToInt()
                                localSettings = localSettings.copy(blurDp = next)
                                onBlurDpChange(next)
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("dim-percent") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_dim_percent),
                            value = localSettings.dimPercent.toFloat(),
                            valueRange = HolographicLauncherSettings.MIN_DIM_PERCENT.toFloat()..
                                HolographicLauncherSettings.MAX_DIM_PERCENT.toFloat(),
                            steps = 12,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_percent_value,
                                localSettings.dimPercent,
                            ),
                            onValueChange = { value ->
                                val next = value.roundToInt()
                                localSettings = localSettings.copy(dimPercent = next)
                                onDimPercentChange(next)
                            },
                        )
                    },
                )
            },
        )
        settingsLazySmallTitle(
            key = "section-haptic",
            title = hapticSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "holographic-haptic",
            selectableGroup = true,
            items = buildList {
                hapticLabels.forEachIndexed { index, label ->
                    add(
                        settingsCardScopeItem("haptic-$index") {
                            SettingRadioRow(
                                title = label,
                                selected = localSettings.hapticLevel == index,
                                onClick = {
                                    localSettings = localSettings.copy(hapticLevel = index)
                                    onHapticLevelChange(index)
                                },
                            )
                        },
                    )
                }
            },
        )
        settingsLazySmallTitle(
            key = "section-apps",
            title = appsSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "holographic-apps",
            items = listOf(
                settingsCardScopeItem("hidden-apps") {
                    val hiddenCount = localSettings.hiddenAppPackages.size
                    HiddenAppsEntryCard(
                        hiddenCount = hiddenCount,
                        titleRes = R.string.holographic_hidden_apps_entry_title,
                        descriptionRes = R.string.holographic_hidden_apps_entry_desc,
                        onClick = onOpenHiddenApps,
                    )
                },
            ),
        )
    }
}

@Composable
fun SettingsCardScope.HolographicLauncherEntryCard(
    hiddenAppCount: Int,
    enabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    val subtitle = if (hiddenAppCount > 0) {
        stringResource(R.string.holographic_launcher_entry_summary_hidden, hiddenAppCount)
    } else {
        stringResource(R.string.holographic_launcher_entry_desc)
    }
    SettingNavigationRow(
        icon = { label ->
            Icon(HubLeadingIcons.holographicLauncher(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.holographic_launcher_entry_title),
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick,
    )
}
