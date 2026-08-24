package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.HolographicLauncherSettings
import com.slideindex.app.ui.miuix.groupedCardItems
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
    onOpenHiddenApps: () -> Unit,
) {
    val interactionSectionTitle = stringResource(R.string.holographic_settings_section_interaction)
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
                            value = settings.timeoutSeconds.toFloat(),
                            valueRange = HolographicLauncherSettings.MIN_TIMEOUT_SECONDS.toFloat()..
                                HolographicLauncherSettings.MAX_TIMEOUT_SECONDS.toFloat(),
                            steps = 59,
                            enabled = true,
                            label = stringResource(
                                R.string.holographic_settings_timeout_value,
                                settings.timeoutSeconds,
                            ),
                            onValueChange = { value ->
                                onTimeoutSecondsChange(value.roundToInt())
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("sensitivity") {
                        SettingsSliderRow(
                            title = stringResource(R.string.holographic_settings_sensitivity),
                            value = settings.rotationSensitivity,
                            valueRange = sensitivityRange,
                            steps = sensitivitySteps,
                            enabled = true,
                            label = stringResource(
                                R.string.holographic_settings_sensitivity_value,
                                (settings.rotationSensitivity * 1000).roundToInt(),
                            ),
                            onValueChange = onRotationSensitivityChange,
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
                                selected = settings.hapticLevel == index,
                                onClick = { onHapticLevelChange(index) },
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
                    val hiddenCount = settings.hiddenAppPackages.size
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
