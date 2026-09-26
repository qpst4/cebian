package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.otp.OtpCodeAlertPolicy
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import kotlin.math.roundToInt

/**
 * 验证码提取：提取成功后做什么（提醒）+ 短信拦截与安全。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpExtractionScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onCodeNotificationChange: (Boolean) -> Unit,
    onCodeNotificationRetentionChange: (Int) -> Unit,
    onShowCodeToastChange: (Boolean) -> Unit,
    onCopyToClipboardChange: (Boolean) -> Unit,
    onBlockCodeSmsChange: (Boolean) -> Unit,
    onMarkSmsReadChange: (Boolean) -> Unit,
    onDeleteSmsAfterExtractChange: (Boolean) -> Unit,
    onOpenSmsBlacklist: () -> Unit,
    onOpenBlockedApps: () -> Unit,
) {
    val context = LocalContext.current
    // 资源读取要跟随配置变化（切语言/改字号/转屏）：用配置感知的派生 Context。
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val resourceContext = androidx.compose.runtime.remember(configuration) {
        context.createConfigurationContext(configuration)
    }
    val alertsItems = listOf(
        settingsCardScopeItem("code-notification") {
            SettingSwitchRow(
                title = stringResource(R.string.otp_code_notification_title),
                subtitle = stringResource(R.string.otp_code_notification_desc),
                checked = settings.otpCodeNotificationEnabled,
                enabled = true,
                onCheckedChange = onCodeNotificationChange,
            )
        },
        if (settings.otpCodeNotificationEnabled && !notificationPermissionGranted) {
            settingsCardScopeItem("code-notification-permission") {
                SettingLinkRow(
                    title = stringResource(R.string.otp_code_notification_permission_title),
                    subtitle = stringResource(R.string.otp_code_notification_permission_desc),
                    onClick = onRequestNotificationPermission,
                )
            }
        } else {
            null
        },
        if (settings.otpCodeNotificationEnabled) {
            settingsCardScopeItem("code-notification-retention") {
                SettingsSliderRow(
                    title = stringResource(R.string.otp_code_notification_retention_title),
                    value = settings.otpCodeNotificationRetentionSeconds.toFloat(),
                    valueRange = 0f..120f,
                    steps = 23,
                    enabled = true,
                    label = if (settings.otpCodeNotificationRetentionSeconds <= OtpCodeAlertPolicy.RETENTION_NEVER_SECONDS) {
                        stringResource(R.string.otp_code_notification_retention_never)
                    } else {
                        stringResource(
                            R.string.otp_code_notification_retention_value,
                            settings.otpCodeNotificationRetentionSeconds,
                        )
                    },
                    formatLabel = { value ->
                        val seconds = value.roundToInt()
                        if (seconds <= OtpCodeAlertPolicy.RETENTION_NEVER_SECONDS) {
                            resourceContext.getString(R.string.otp_code_notification_retention_never)
                        } else {
                            resourceContext.getString(R.string.otp_code_notification_retention_value, seconds)
                        }
                    },
                    snapValue = { value -> (value / 5f).roundToInt() * 5f },
                    onValueChange = { value ->
                        onCodeNotificationRetentionChange(((value / 5f).roundToInt() * 5).coerceIn(0, 120))
                    },
                )
            }
        } else {
            null
        },
        settingsCardScopeItem("copy-to-clipboard") {
            SettingSwitchRow(
                title = stringResource(R.string.otp_copy_to_clipboard_title),
                subtitle = stringResource(R.string.otp_copy_to_clipboard_desc),
                checked = settings.otpCopyToClipboard,
                enabled = true,
                onCheckedChange = onCopyToClipboardChange,
            )
        },
        settingsCardScopeItem("code-toast") {
            SettingSwitchRow(
                title = stringResource(R.string.otp_show_code_toast_title),
                subtitle = stringResource(R.string.otp_show_code_toast_desc),
                checked = settings.otpShowCodeToast,
                enabled = true,
                onCheckedChange = onShowCodeToastChange,
            )
        },
    ).filterNotNull()

    val interceptItems = listOf(
        settingsCardScopeItem("block-code-sms") {
            SettingSwitchRow(
                title = stringResource(R.string.otp_block_code_sms_title),
                subtitle = stringResource(R.string.otp_block_code_sms_desc),
                checked = settings.otpBlockCodeSmsEnabled,
                enabled = true,
                onCheckedChange = onBlockCodeSmsChange,
            )
        },
        settingsCardScopeItem("mark-sms-read") {
            SettingSwitchRow(
                title = stringResource(R.string.otp_mark_sms_read_title),
                subtitle = stringResource(R.string.otp_mark_sms_read_desc),
                checked = settings.otpMarkSmsReadEnabled,
                enabled = true,
                onCheckedChange = onMarkSmsReadChange,
            )
        },
        settingsCardScopeItem("delete-sms-after-extract") {
            SettingSwitchRow(
                title = stringResource(R.string.otp_delete_sms_after_extract_title),
                subtitle = stringResource(R.string.otp_delete_sms_after_extract_desc),
                checked = settings.otpDeleteSmsAfterExtractEnabled,
                enabled = true,
                onCheckedChange = onDeleteSmsAfterExtractChange,
            )
        },
        settingsCardScopeItem("sms-blacklist-entry") {
            SettingLinkRow(
                title = stringResource(R.string.otp_sms_blacklist_section),
                subtitle = stringResource(R.string.otp_sms_blacklist_entry_desc),
                onClick = onOpenSmsBlacklist,
            )
        },
        settingsCardScopeItem("blocked-apps-entry") {
            SettingLinkRow(
                title = stringResource(R.string.otp_blocked_apps_entry_title),
                subtitle = stringResource(R.string.otp_blocked_apps_entry_desc),
                onClick = onOpenBlockedApps,
            )
        },
    )

    val alertsTitle = stringResource(R.string.otp_alerts_section)
    val interceptTitle = stringResource(R.string.otp_intercept_section)
    val interceptTip = stringResource(R.string.otp_intercept_tip)
    SettingsLazyScreenScaffold(
        title = stringResource(R.string.otp_extraction_entry_title),
        subtitle = stringResource(R.string.otp_extraction_entry_desc),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(key = "otp_alerts_section", title = alertsTitle)
        groupedCardItems(keyPrefix = "otp-alerts", items = alertsItems)

        settingsLazySmallTitle(key = "otp_intercept_section", title = interceptTitle)
        settingsLazyTipCard(key = "otp_intercept_tip", text = interceptTip)
        groupedCardItems(keyPrefix = "otp-intercept", items = interceptItems)
    }
}
