package com.slideindex.app.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.otp.LsposedInjectorProbe
import com.slideindex.app.otp.OtpAutoFillStats
import com.slideindex.app.otp.OtpAutoFillUiLabels
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.settings.components.SettingsCardLazyGroup
import com.slideindex.app.ui.settings.components.SettingsCardRow
import com.slideindex.app.ui.settings.components.emitSettingsCardGroup
import com.slideindex.app.ui.settings.components.rememberSettingsCardGroup
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.miuix.MiuixHintText
import kotlin.math.roundToInt

data class OtpAutoInputSettingsLazyGroups(
    val runtimeStatus: SettingsCardLazyGroup,
    val autoFill: SettingsCardLazyGroup,
    val a11ySetup: SettingsCardLazyGroup?,
    val lsposed: SettingsCardLazyGroup,
    val diagnostics: SettingsCardLazyGroup,
    val timing: SettingsCardLazyGroup,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberOtpAutoInputSettingsLazyGroups(
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
): OtpAutoInputSettingsLazyGroups {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val runtimeStatus = rememberSettingsCardGroup("otp-runtime-status") {
        SettingsCardRow(key = "runtime-status-main") { _ ->
            MiuixHintText(OtpAutoFillUiLabels.formatRuntimeStatus(context, settings, accessibilityGranted))
        }
        OtpAutoFillUiLabels.formatFillPipelineHint(context, settings, accessibilityGranted)?.let { hint ->
            SettingsCardRow(key = "runtime-pipeline-hint") { _ ->
                MiuixHintText(hint)
            }
        }
        OtpAutoFillUiLabels.formatRuntimeSmsHint(context, settings)?.let { hint ->
            SettingsCardRow(key = "runtime-sms-hint") { _ ->
                MiuixHintText(hint)
            }
        }
        if (settings.otpLsposedSystemInjectEnabled && settings.otpAutoInputEnabled && accessibilityGranted) {
            SettingsCardRow(key = "runtime-a11y-fallback") { _ ->
                MiuixHintText(stringResource(R.string.otp_fill_method_a11y_fallback_hint))
            }
        }
    }

    val autoFill = rememberSettingsCardGroup("otp-auto-fill") {
        SettingSwitchRow(
            title = stringResource(R.string.otp_auto_input_enabled_title),
            subtitle = stringResource(R.string.otp_auto_input_enabled_desc),
            icon = { label -> Icon(Icons.Outlined.Keyboard, contentDescription = label) },
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
            icon = { label -> Icon(Icons.Outlined.ContentCopy, contentDescription = label) },
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

    val a11ySetup = if (!accessibilityGranted) {
        rememberSettingsCardGroup("otp-a11y-setup") {
            SettingLinkRow(
                title = stringResource(R.string.otp_auto_input_service_setup_title),
                subtitle = stringResource(R.string.otp_auto_input_service_setup_desc),
                onClick = onRequestAccessibility,
            )
        }
    } else {
        null
    }

    val lsposed = rememberSettingsCardGroup("otp-lsposed") {
        SettingSwitchRow(
            title = stringResource(R.string.otp_lsposed_sms_title),
            subtitle = stringResource(R.string.otp_lsposed_sms_desc_short),
            icon = { label -> Icon(Icons.Outlined.Security, contentDescription = label) },
            checked = settings.otpLsposedSmsCaptureEnabled,
            enabled = true,
            onCheckedChange = onLsposedSmsChange,
        )
        SettingSwitchRow(
            title = stringResource(R.string.otp_lsposed_inject_title),
            subtitle = stringResource(R.string.otp_lsposed_inject_desc_short),
            icon = { label -> Icon(Icons.Outlined.Keyboard, contentDescription = label) },
            checked = settings.otpLsposedSystemInjectEnabled,
            enabled = accessibilityGranted && settings.otpAutoInputEnabled,
            onCheckedChange = onLsposedSystemInjectChange,
        )
    }

    var probeMessage by remember { mutableStateOf<String?>(null) }
    var probeRunning by remember { mutableStateOf(false) }
    val diagnostics = rememberSettingsCardGroup("otp-diagnostics") {
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
                icon = { label -> Icon(Icons.Outlined.Analytics, contentDescription = label) },
                title = stringResource(R.string.otp_autofill_stats_title),
                subtitle = OtpAutoFillUiLabels.formatStatsEntrySubtitle(context, stats),
                onClick = onOpenStats,
            )
        }
    }

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
    val timing = rememberSettingsCardGroup("otp-timing") {
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

    return remember(
        runtimeStatus,
        autoFill,
        a11ySetup,
        lsposed,
        diagnostics,
        timing,
    ) {
        OtpAutoInputSettingsLazyGroups(
            runtimeStatus = runtimeStatus,
            autoFill = autoFill,
            a11ySetup = a11ySetup,
            lsposed = lsposed,
            diagnostics = diagnostics,
            timing = timing,
        )
    }
}

fun LazyListScope.emitOtpAutoInputSettingsItems(
    groups: OtpAutoInputSettingsLazyGroups,
    runtimeSectionTitle: String,
    autoFillSectionTitle: String,
    lsposedSectionTitle: String,
    lsposedSectionDesc: String,
    diagnosticsSectionTitle: String,
    timingSectionTitle: String,
) {
    settingsLazySmallTitle(
        key = "otp_runtime_section",
        title = runtimeSectionTitle,
        sectionTop = true,
    )
    emitSettingsCardGroup(groups.runtimeStatus)

    settingsLazySmallTitle(
        key = "otp_auto_fill_section",
        title = autoFillSectionTitle,
        sectionTop = true,
    )
    emitSettingsCardGroup(groups.autoFill)

    groups.a11ySetup?.let { emitSettingsCardGroup(it) }

    settingsLazySmallTitle(
        key = "otp_lsposed_section",
        title = lsposedSectionTitle,
        sectionTop = true,
    )
    item(key = "otp_lsposed_desc") {
        SettingsHintText(lsposedSectionDesc)
    }
    emitSettingsCardGroup(groups.lsposed)

    settingsLazySmallTitle(
        key = "otp_diagnostics_section",
        title = diagnosticsSectionTitle,
        sectionTop = true,
    )
    emitSettingsCardGroup(groups.diagnostics)

    settingsLazySmallTitle(
        key = "otp_timing_section",
        title = timingSectionTitle,
        sectionTop = true,
    )
    emitSettingsCardGroup(groups.timing)
}
