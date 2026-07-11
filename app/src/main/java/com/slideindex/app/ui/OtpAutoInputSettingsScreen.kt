package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.otp.LsposedInjectorProbe
import com.slideindex.app.settings.AppSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpAutoInputSettingsScreen(
    settings: AppSettings,
    accessibilityGranted: Boolean,
    onBack: (() -> Unit)?,
    onRequestAccessibility: () -> Unit,
    onAutoInputChange: (Boolean) -> Unit,
    onAutoConfirmChange: (Boolean) -> Unit,
    onDelayChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onLsposedSmsChange: (Boolean) -> Unit = {},
    onLsposedSystemInjectChange: (Boolean) -> Unit = {},
    onCopyToClipboardChange: (Boolean) -> Unit = {},
) {
    SettingsScreenScaffold(
        title = stringResource(R.string.otp_auto_input_title),
        subtitle = stringResource(R.string.otp_auto_input_desc),
        onBack = onBack,
        embedded = onBack == null,
        modifier = modifier,
    ) {
        val appContext = LocalContext.current.applicationContext
        val formatDelayLabel = remember(appContext) {
            { value: Float ->
                if (value.roundToInt() <= 0) {
                    appContext.getString(R.string.otp_auto_input_delay_zero)
                } else {
                    appContext.getString(R.string.otp_auto_input_delay_value, value.roundToInt())
                }
            }
        }
        val formatIntervalLabel = remember(appContext) {
            { value: Float ->
                appContext.getString(R.string.otp_auto_input_interval_value, value.roundToInt())
            }
        }

        SettingsSectionTitle(stringResource(R.string.otp_extraction_extensions_section))
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.otp_auto_input_enabled_title),
                subtitle = stringResource(R.string.otp_auto_input_enabled_desc),
                icon = { Icon(Icons.Default.Keyboard, contentDescription = null) },
                checked = settings.otpAutoInputEnabled,
                enabled = accessibilityGranted,
                onCheckedChange = { enabled ->
                    if (!accessibilityGranted) {
                        onRequestAccessibility()
                    } else {
                        onAutoInputChange(enabled)
                    }
                },
            )
            SettingSwitchRow(
                title = stringResource(R.string.otp_copy_to_clipboard_title),
                subtitle = stringResource(R.string.otp_copy_to_clipboard_desc),
                icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                checked = settings.otpCopyToClipboard,
                enabled = true,
                onCheckedChange = onCopyToClipboardChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.otp_auto_confirm_title),
                subtitle = stringResource(R.string.otp_auto_confirm_desc),
                checked = settings.otpAutoConfirmEnabled,
                enabled = accessibilityGranted && settings.otpAutoInputEnabled,
                onCheckedChange = onAutoConfirmChange,
            )
        }

        if (!accessibilityGranted) {
            SettingsSectionTitle(stringResource(R.string.otp_auto_input_service_section))
            SettingsCard {
                SettingLinkRow(
                    title = stringResource(R.string.otp_auto_input_service_setup_title),
                    subtitle = stringResource(R.string.otp_auto_input_service_setup_desc),
                    onClick = onRequestAccessibility,
                )
            }
        } else {
            SettingsHintText(stringResource(R.string.otp_auto_input_service_ready))
        }

        SettingsSectionTitle(stringResource(R.string.otp_lsposed_extensions_section))
        SettingsHintText(
            if (settings.otpLsposedSystemInjectEnabled) {
                stringResource(R.string.otp_fill_method_pipeline_inject)
            } else {
                stringResource(R.string.otp_fill_method_pipeline_a11y_only)
            },
        )
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.otp_lsposed_sms_title),
                subtitle = stringResource(R.string.otp_lsposed_sms_desc_short),
                icon = { Icon(Icons.Default.Security, contentDescription = null) },
                checked = settings.otpLsposedSmsCaptureEnabled,
                enabled = true,
                onCheckedChange = onLsposedSmsChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.otp_lsposed_inject_title),
                subtitle = stringResource(R.string.otp_lsposed_inject_desc_short),
                icon = { Icon(Icons.Default.Keyboard, contentDescription = null) },
                checked = settings.otpLsposedSystemInjectEnabled,
                enabled = accessibilityGranted && settings.otpAutoInputEnabled,
                onCheckedChange = onLsposedSystemInjectChange,
            )
        }
        SettingsHintText(stringResource(R.string.otp_fill_method_a11y_fallback_hint))

        var probeMessage by remember { mutableStateOf<String?>(null) }
        var probeRunning by remember { mutableStateOf(false) }
        SettingsCard {
            SettingLinkRow(
                title = stringResource(R.string.otp_lsposed_probe_title),
                subtitle = when {
                    probeRunning -> stringResource(R.string.otp_lsposed_probe_checking)
                    probeMessage != null -> probeMessage!!
                    else -> stringResource(R.string.otp_lsposed_probe_desc)
                },
                onClick = {
                    if (probeRunning) return@SettingLinkRow
                    probeRunning = true
                    probeMessage = null
                    LsposedInjectorProbe.probe(appContext) { status, detail ->
                        probeRunning = false
                        probeMessage = detail
                    }
                },
            )
        }
        SettingsHintText(stringResource(R.string.otp_lsposed_scope_hint))

        SettingsSectionTitle(stringResource(R.string.otp_auto_input_timing_section))
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.otp_auto_input_delay_title),
                value = settings.otpAutoInputDelayMs.toFloat(),
                valueRange = 0f..3000f,
                steps = 29,
                enabled = accessibilityGranted && settings.otpAutoInputEnabled,
                label = formatDelayLabel(settings.otpAutoInputDelayMs.toFloat()),
                formatLabel = formatDelayLabel,
                snapValue = { value -> (value / 100f).roundToInt() * 100f },
                onValueChange = { value ->
                    onDelayChange(((value / 100f).roundToInt() * 100).coerceIn(0, 3000))
                },
            )
            SettingsSliderRow(
                title = stringResource(R.string.otp_auto_input_interval_title),
                value = settings.otpAutoInputIntervalMs.toFloat(),
                valueRange = 0f..500f,
                steps = 24,
                enabled = accessibilityGranted && settings.otpAutoInputEnabled,
                label = formatIntervalLabel(settings.otpAutoInputIntervalMs.toFloat()),
                formatLabel = formatIntervalLabel,
                snapValue = { value -> (value / 20f).roundToInt() * 20f },
                onValueChange = { value ->
                    onIntervalChange(((value / 20f).roundToInt() * 20).coerceIn(0, 500))
                },
            )
        }
    }
}
