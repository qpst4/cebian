package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppLaunchPolicy
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.descRes
import com.slideindex.app.settings.resolvedFreeWindowMode
import com.slideindex.app.settings.resolvedLaunchPolicy
import com.slideindex.app.settings.titleRes
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FreeWindowSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onLongPressDurationChange: (Int) -> Unit,
    onLaunchPolicyChange: (Int) -> Unit,
    onOpenMode: () -> Unit,
    onOpenPreview: () -> Unit,
) {
    val selectedMode = settings.resolvedFreeWindowMode()
    val selectedPolicy = settings.resolvedLaunchPolicy()
    val longPressDuration = settings.effectiveLongPressDurationMs()
    val showLongPressDuration = selectedPolicy.usesLongPress()
    val resources = LocalResources.current
    val formatDurationLabel = remember(resources) {
        { ms: Float ->
            resources.getString(R.string.long_press_launch_duration_value, ms.roundToInt())
        }
    }
    val launchPolicyEntries = AppLaunchPolicy.entries
    val launchPolicyIndex = launchPolicyEntries.indexOf(selectedPolicy).coerceAtLeast(0)

    val serviceSectionTitle = stringResource(R.string.settings_section_service)
    val portraitOnlyHint = stringResource(R.string.free_window_portrait_only_hint)
    val launchSectionTitle = stringResource(R.string.settings_section_launch)
    val longPressDurationDesc = stringResource(R.string.long_press_launch_duration_desc)
    val freeWindowSectionTitle = stringResource(R.string.settings_section_free_window)
    val freeWindowModeHint = stringResource(R.string.free_window_mode_hint)

    SettingsScreenScaffold(
        title = stringResource(R.string.free_window_settings_title),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "section-service",
            title = serviceSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "free-window-service",
            items = buildList {
                add(
                    settingsCardScopeItem("free-window-enabled") {
                        SettingToggleRow(
                            icon = { label -> Icon(Icons.Outlined.PowerSettingsNew, contentDescription = label) },
                            title = stringResource(R.string.free_window_enabled),
                            subtitle = stringResource(R.string.free_window_enabled_desc),
                            checked = settings.freeWindowEnabled,
                            onCheckedChange = onEnabledChange,
                        )
                    },
                )
            },
        )
        settingsLazyHint(
            key = "free-window-portrait-hint",
            text = portraitOnlyHint,
        )
        settingsLazySmallTitle(
            key = "section-launch",
            title = launchSectionTitle,
            sectionTop = true,
        )
        if (showLongPressDuration) {
            settingsLazyHint(
                key = "long-press-duration-hint",
                text = longPressDurationDesc,
            )
        }
        groupedCardItems(
            keyPrefix = "free-window-launch-policy",
            items = buildList {
                add(
                    settingsCardScopeItem("launch-policy") {
                        SettingDropdownRow(
                            icon = { label ->
                                Icon(Icons.AutoMirrored.Outlined.Launch, contentDescription = label)
                            },
                            title = stringResource(R.string.launch_policy_title),
                            subtitle = stringResource(selectedPolicy.descRes),
                            items = launchPolicyEntries.map { stringResource(it.titleRes) },
                            selectedIndex = launchPolicyIndex,
                            enabled = settings.freeWindowEnabled,
                            onSelectedIndexChange = { onLaunchPolicyChange(launchPolicyEntries[it].id) },
                        )
                    },
                )
                if (showLongPressDuration) {
                    add(
                        settingsCardScopeItem("long-press-duration") {
                            SettingsSliderRow(
                                title = stringResource(R.string.long_press_launch_duration),
                                value = longPressDuration.toFloat(),
                                valueRange = 250f..900f,
                                steps = 12,
                                enabled = settings.freeWindowEnabled,
                                label = formatDurationLabel(longPressDuration.toFloat()),
                                snapValue = { value ->
                                    ((value / 50f).roundToInt() * 50).toFloat().coerceIn(250f, 900f)
                                },
                                formatLabel = formatDurationLabel,
                                onValueChange = { value -> onLongPressDurationChange(value.roundToInt()) },
                            )
                        },
                    )
                }
            },
        )
        settingsLazySmallTitle(
            key = "section-free-window",
            title = freeWindowSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "free-window-mode",
            items = buildList {
                add(
                    settingsCardScopeItem("free-window-launch-mode") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Outlined.Layers, contentDescription = label) },
                            title = stringResource(R.string.free_window_launch_mode),
                            subtitle = stringResource(selectedMode.titleRes),
                            enabled = settings.freeWindowEnabled,
                            onClick = onOpenMode,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("free-window-adjust-layout") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Outlined.AspectRatio, contentDescription = label) },
                            title = stringResource(R.string.free_window_adjust_layout),
                            subtitle = stringResource(R.string.free_window_adjust_layout_desc),
                            enabled = settings.freeWindowEnabled,
                            onClick = onOpenPreview,
                        )
                    },
                )
            },
        )
        settingsLazyHint(
            key = "free-window-mode-hint",
            text = freeWindowModeHint,
        )
    }
}

@Composable
fun SettingsCardScope.FreeWindowEntryCard(
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label ->
            Icon(HomeLeadingIcons.freeWindow(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.free_window_entry_title),
        subtitle = stringResource(R.string.free_window_entry_desc),
        onClick = onClick,
    )
}
