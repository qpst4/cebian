package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    settings: AppSettings,
    overlayGranted: Boolean,
    notificationGranted: Boolean,
    shizukuGranted: Boolean,
    accessibilityGranted: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onServiceEnabledChange: (Boolean) -> Unit,
    onHapticEnabledChange: (Boolean) -> Unit,
    onHapticStrengthChange: (Int) -> Unit,
    onOpenLayoutSettings: () -> Unit,
    onOpenFreeWindowSettings: () -> Unit,
    onOpenHiddenAppsSettings: () -> Unit,
    onOpenExcludedAppsSettings: () -> Unit,
    onOpenSideGesturesLeft: () -> Unit,
    onOpenSideGesturesRight: () -> Unit,
    onThemeColorChange: (Int) -> Unit,
) {
    val permissionsReady = overlayGranted && notificationGranted

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!overlayGranted) {
                PermissionCard(
                    title = stringResource(R.string.permission_overlay_title),
                    description = stringResource(R.string.permission_overlay_desc),
                    onGrant = onRequestOverlay,
                )
            }
            if (!notificationGranted) {
                PermissionCard(
                    title = stringResource(R.string.permission_notification_title),
                    description = stringResource(R.string.permission_notification_desc),
                    onGrant = onRequestNotification,
                )
            }
            if (!shizukuGranted) {
                PermissionCard(
                    title = stringResource(R.string.permission_shizuku_title),
                    description = stringResource(R.string.permission_shizuku_desc),
                    onGrant = onRequestShizuku,
                    grantLabel = stringResource(R.string.permission_shizuku_grant),
                )
            }
            if (!accessibilityGranted) {
                PermissionCard(
                    title = stringResource(R.string.permission_accessibility_title),
                    description = stringResource(R.string.permission_accessibility_desc),
                    onGrant = onRequestAccessibility,
                    grantLabel = stringResource(R.string.permission_accessibility_grant),
                )
            }

            SettingsSectionTitle(stringResource(R.string.settings_section_service))
            SettingsCard {
                SettingSwitchRow(
                    title = stringResource(R.string.service_enabled),
                    checked = settings.serviceEnabled,
                    enabled = permissionsReady,
                    onCheckedChange = onServiceEnabledChange,
                )
            }
            if (settings.serviceEnabled && overlayGranted) {
                Text(
                    text = stringResource(R.string.ready_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            SettingsSectionTitle(stringResource(R.string.settings_section_layout))
            SettingsCard {
                LayoutSettingsEntryCard(
                    settings = settings,
                    enabled = settings.serviceEnabled,
                    onClick = onOpenLayoutSettings,
                )
            }

            SettingsSectionTitle(stringResource(R.string.settings_section_gestures))
            SettingsCard {
                SideGesturesEntryCard(
                    onOpenLeft = onOpenSideGesturesLeft,
                    onOpenRight = onOpenSideGesturesRight,
                )
            }

            SettingsSectionTitle(stringResource(R.string.settings_section_apps))
            SettingsCard {
                HiddenAppsEntryCard(
                    hiddenCount = settings.hiddenAppPackages.size,
                    onClick = onOpenHiddenAppsSettings,
                )
                ExcludedAppsEntryCard(
                    excludedCount = settings.excludedTriggerAppPackages.size,
                    onClick = onOpenExcludedAppsSettings,
                )
                FreeWindowEntryCard(onClick = onOpenFreeWindowSettings)
            }

            SettingsSectionTitle(stringResource(R.string.settings_section_feedback))
            SettingsCard {
                SettingSwitchRow(
                    title = stringResource(R.string.haptic_enabled),
                    checked = settings.hapticEnabled,
                    enabled = true,
                    onCheckedChange = onHapticEnabledChange,
                )
                if (settings.hapticEnabled) {
                    SettingsSliderRow(
                        title = stringResource(R.string.haptic_strength),
                        value = settings.hapticStrengthLevel.toFloat(),
                        valueRange = 0f..2f,
                        steps = 1,
                        enabled = true,
                        label = hapticStrengthLabel(settings.hapticStrengthLevel),
                        onValueChange = { onHapticStrengthChange(it.roundToInt()) },
                    )
                }
            }

            SettingsSectionTitle(stringResource(R.string.settings_section_appearance))
            SettingsCard {
                ThemeColorPicker(
                    selected = settings.themeColorArgb,
                    enabled = settings.serviceEnabled,
                    onColorSelected = onThemeColorChange,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun hapticStrengthLabel(level: Int): String {
    return when (level) {
        0 -> stringResource(R.string.haptic_strength_light)
        2 -> stringResource(R.string.haptic_strength_strong)
        else -> stringResource(R.string.haptic_strength_medium)
    }
}
