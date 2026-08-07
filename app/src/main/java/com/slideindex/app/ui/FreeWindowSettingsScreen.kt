package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.resolvedFreeWindowMode
import com.slideindex.app.settings.resolvedLaunchPolicy
import com.slideindex.app.settings.titleRes
import com.slideindex.app.ui.HomeLeadingIcons
import com.slideindex.app.ui.settings.components.SettingsCardScope
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FreeWindowSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onLongPressDurationChange: (Int) -> Unit,
    onOpenLaunchPolicy: () -> Unit,
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

    SettingsScreenScaffold(
        title = stringResource(R.string.free_window_settings_title),
        onBack = onBack,
    ) {
        MiuixSmallTitle(stringResource(R.string.settings_section_service), modifier = Modifier.fillMaxWidth())
        SettingsCard {
                SettingToggleRow(
                    icon = { label -> Icon(Icons.Outlined.PowerSettingsNew, contentDescription = label) },
                    title = stringResource(R.string.free_window_enabled),
                    subtitle = stringResource(R.string.free_window_enabled_desc),
                    checked = settings.freeWindowEnabled,
                    onCheckedChange = onEnabledChange,
                )
            }
        SettingsHintText(stringResource(R.string.free_window_portrait_only_hint))

        MiuixSmallTitle(stringResource(R.string.settings_section_launch), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsCard {
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.AutoMirrored.Outlined.Launch, contentDescription = label) },
                    title = stringResource(R.string.launch_policy_title),
                    subtitle = stringResource(selectedPolicy.titleRes),
                    enabled = settings.freeWindowEnabled,
                    onClick = onOpenLaunchPolicy,
                )
            }
            if (showLongPressDuration) {
                SettingsHintText(stringResource(R.string.long_press_launch_duration_desc))
                SettingsCard {
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
                }
            }

        MiuixSmallTitle(stringResource(R.string.settings_section_free_window), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsCard {
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Outlined.Layers, contentDescription = label) },
                    title = stringResource(R.string.free_window_launch_mode),
                    subtitle = stringResource(selectedMode.titleRes),
                    enabled = settings.freeWindowEnabled,
                    onClick = onOpenMode,
                )
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Outlined.AspectRatio, contentDescription = label) },
                    title = stringResource(R.string.free_window_adjust_layout),
                    subtitle = stringResource(R.string.free_window_adjust_layout_desc),
                    enabled = settings.freeWindowEnabled,
                    onClick = onOpenPreview,
                )
            }
        SettingsHintText(stringResource(R.string.free_window_mode_hint))
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
