package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
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
import com.slideindex.app.otp.OtpAutoFillStats
import com.slideindex.app.otp.OtpAutoFillUiLabels
import com.slideindex.app.settings.AppSettings
import kotlin.math.roundToInt

fun LazyListScope.otpAutoInputSettingsItems(
    settings: AppSettings,
    accessibilityGranted: Boolean,
    onRequestAccessibility: () -> Unit,
    onAutoInputChange: (Boolean) -> Unit,
    onAutoConfirmChange: (Boolean) -> Unit,
    onDelayChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onLsposedSmsChange: (Boolean) -> Unit,
    onLsposedSystemInjectChange: (Boolean) -> Unit,
    onCopyToClipboardChange: (Boolean) -> Unit,
    stats: OtpAutoFillStats?,
    onOpenStats: (() -> Unit)?,
) {
    item(key = "otp_runtime_section") {
        val context = LocalContext.current
        MiuixSmallTitle(stringResource(R.string.otp_runtime_status_section), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsCard {
            SettingsHintText(OtpAutoFillUiLabels.formatRuntimeStatus(context, settings, accessibilityGranted))
            OtpAutoFillUiLabels.formatFillPipelineHint(context, settings, accessibilityGranted)?.let {
                SettingsHintText(it)
            }
            OtpAutoFillUiLabels.formatRuntimeSmsHint(context, settings)?.let {
                SettingsHintText(it)
            }
            if (settings.otpLsposedSystemInjectEnabled && settings.otpAutoInputEnabled && accessibilityGranted) {
                SettingsHintText(stringResource(R.string.otp_fill_method_a11y_fallback_hint))
            }
        }
    }
    item(key = "otp_auto_fill_section") {
        MiuixSmallTitle(stringResource(R.string.otp_auto_fill_section), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.otp_auto_input_enabled_title),
                subtitle = stringResource(R.string.otp_auto_input_enabled_desc),
                icon = { label -> Icon(Icons.Default.Keyboard, contentDescription = label) },
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
                icon = { label -> Icon(Icons.Default.ContentCopy, contentDescription = label) },
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
    }
    if (!accessibilityGranted) {
        item(key = "otp_a11y_setup") {
            SettingsCard {
                SettingLinkRow(
                    title = stringResource(R.string.otp_auto_input_service_setup_title),
                    subtitle = stringResource(R.string.otp_auto_input_service_setup_desc),
                    onClick = onRequestAccessibility,
                )
            }
        }
    }
    item(key = "otp_lsposed_section") {
        MiuixSmallTitle(stringResource(R.string.otp_lsposed_enhancements_section), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsHintText(stringResource(R.string.otp_lsposed_enhancements_desc))
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.otp_lsposed_sms_title),
                subtitle = stringResource(R.string.otp_lsposed_sms_desc_short),
                icon = { label -> Icon(Icons.Default.Security, contentDescription = label) },
                checked = settings.otpLsposedSmsCaptureEnabled,
                enabled = true,
                onCheckedChange = onLsposedSmsChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.otp_lsposed_inject_title),
                subtitle = stringResource(R.string.otp_lsposed_inject_desc_short),
                icon = { label -> Icon(Icons.Default.Keyboard, contentDescription = label) },
                checked = settings.otpLsposedSystemInjectEnabled,
                enabled = accessibilityGranted && settings.otpAutoInputEnabled,
                onCheckedChange = onLsposedSystemInjectChange,
            )
        }
    }
    item(key = "otp_diagnostics_section") {
        val context = LocalContext.current
        val appContext = context.applicationContext
        var probeMessage by remember { mutableStateOf<String?>(null) }
        var probeRunning by remember { mutableStateOf(false) }
        MiuixSmallTitle(stringResource(R.string.otp_diagnostics_section), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
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
                    LsposedInjectorProbe.probe(appContext) { _, detail ->
                        probeRunning = false
                        probeMessage = detail
                    }
                },
            )
            if (stats != null && onOpenStats != null) {
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.Analytics, contentDescription = label) },
                    title = stringResource(R.string.otp_autofill_stats_title),
                    subtitle = OtpAutoFillUiLabels.formatStatsEntrySubtitle(context, stats),
                    onClick = onOpenStats,
                )
            }
        }
    }
    item(key = "otp_timing_section") {
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
        MiuixSmallTitle(stringResource(R.string.otp_auto_input_timing_section), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
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
