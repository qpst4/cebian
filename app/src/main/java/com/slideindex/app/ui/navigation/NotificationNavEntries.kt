package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import com.slideindex.app.ui.MessageReminderAllowedAppsScreen
import com.slideindex.app.ui.MessageReminderDndAppsScreen
import com.slideindex.app.ui.MessageReminderSettingsScreen
import com.slideindex.app.ui.MessageStyleSettingsScreen
import com.slideindex.app.ui.NotificationHistoryScreen
import com.slideindex.app.ui.NotificationHubScreen
import com.slideindex.app.ui.OtpAutoInputSettingsScreen
import com.slideindex.app.ui.OtpHubScreen
import com.slideindex.app.ui.OtpRecordsScreen
import com.slideindex.app.ui.OtpRulesListScreen
import com.slideindex.app.ui.OtpSettingsScreen
import com.slideindex.app.ui.viewmodel.NotificationHubViewModel
import com.slideindex.app.ui.viewmodel.notificationHubViewModelFactory

fun EntryProviderScope<AppNavKey>.notificationNavEntries(ctx: MainNavContext) {
    entry<AppNavKey.NotificationHub> {
        val permissions = ctx.collectPermissions()
        val viewModel: NotificationHubViewModel = viewModel(
            factory = notificationHubViewModelFactory(ctx.app),
        )
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val visibleHistoryCount by viewModel.visibleHistoryCount.collectAsStateWithLifecycle()
        NotificationHubScreen(
            notificationListenerEnabled = permissions.notificationListenerEnabled,
            messageReminderEnabled = settings.messageReminderSettings.enabled,
            messageReminderSettings = settings.messageReminderSettings,
            notificationHistoryCount = visibleHistoryCount,
            onOpenNotificationHistory = { ctx.navigate(AppNavKey.NotificationHistory) },
            onOpenOtpHub = { ctx.navigate(AppNavKey.OtpHub) },
            onOpenMessageReminder = { ctx.navigate(AppNavKey.MessageReminder) },
            bottomContentPadding = ctx.rootBottomContentPadding,
        )
    }

    entry<AppNavKey.NotificationHistory> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        NotificationHistoryScreen(
            listenerEnabled = permissions.notificationListenerEnabled,
            onBack = { ctx.navigateBackTo(AppNavKey.NotificationHub) },
            onRequestListenerAccess = { ctx.openNotificationListenerSettings() },
        )
    }

    entry<AppNavKey.MessageReminder> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        MessageReminderSettingsScreen(
            settings = settings.messageReminderSettings,
            notificationListenerEnabled = permissions.notificationListenerEnabled,
            bottomContentPadding = ctx.rootBottomContentPadding,
            onBack = { ctx.navigateBackTo(AppNavKey.NotificationHub) },
            onEnabledChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageReminderEnabled(enabled)
                }
            },
            onOpenStyleSettings = { ctx.navigate(AppNavKey.MessageStyle) },
            onHideInLandscapeChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageHideInLandscape(enabled)
                }
            },
            onPortraitDanmakuChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessagePortraitDanmaku(enabled)
                }
            },
            onLandscapeDanmakuChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageLandscapeDanmaku(enabled)
                }
            },
            onGestureActionChange = { slot, action ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageGestureAction(slot, action)
                }
            },
            onOpenAllowedApps = { ctx.navigate(AppNavKey.MessageReminderAllowedApps) },
            onOpenDndApps = { ctx.navigate(AppNavKey.MessageReminderDndApps) },
            onSuppressWhenSystemDndChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageSuppressWhenSystemDnd(enabled)
                }
            },
            onOpenOverlayPermission = { ctx.openOverlaySettings() },
            onOpenNotificationListenerPermission = { ctx.openNotificationListenerSettings() },
        )
    }

    entry<AppNavKey.MessageReminderAllowedApps> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        MessageReminderAllowedAppsScreen(
            settings = settings.messageReminderSettings,
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminder) },
            onAddPackage = { packageName ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.addMessageEnabledPackage(packageName)
                }
            },
            onRemovePackage = { packageName ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.removeMessageEnabledPackage(packageName)
                }
            },
            onSaveFilterRule = { rule ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.upsertMessageAppFilterRule(rule)
                }
            },
        )
    }

    entry<AppNavKey.MessageReminderDndApps> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        MessageReminderDndAppsScreen(
            dndPackages = settings.messageReminderSettings.dndPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminder) },
            onAddPackage = { packageName ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.addMessageDndPackage(packageName)
                }
            },
            onRemovePackage = { packageName ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.removeMessageDndPackage(packageName)
                }
            },
        )
    }

    entry<AppNavKey.MessageStyle> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        MessageStyleSettingsScreen(
            settings = settings.messageReminderSettings,
            bottomContentPadding = ctx.rootBottomContentPadding,
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminder) },
            onStyleIdChange = { styleId ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageStyleId(styleId)
                }
            },
            onThemeIdChange = { themeId ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageThemeId(themeId)
                }
            },
            onPrimaryStyleEnabledChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessagePrimaryStyleEnabled(enabled)
                }
            },
            onDanmakuEnabledChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageDanmakuEnabled(enabled)
                }
            },
            onDanmakuThemeIdChange = { themeId ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageDanmakuThemeId(themeId)
                }
            },
            onFloatIconOpacityChange = { opacity ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageFloatIconOpacity(opacity)
                }
            },
            onCardOpacityChange = { opacity ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageCardOpacity(opacity)
                }
            },
            onSideBubbleOpacityChange = { opacity ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageSideBubbleOpacity(opacity)
                }
            },
            onDanmakuOpacityChange = { opacity ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageDanmakuOpacity(opacity)
                }
            },
            onCardMaxLinesChange = { lines ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageCardMaxLines(lines)
                }
            },
            onDanmakuMaxLinesChange = { lines ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageDanmakuMaxLines(lines)
                }
            },
            onSideMaxCountChange = { count ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageSideMaxCount(count)
                }
            },
            onSideMaxWidthDpChange = { width ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageSideMaxWidthDp(width)
                }
            },
            onSideMaxLinesChange = { lines ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageSideMaxLines(lines)
                }
            },
            onAutoDismissSecondsChange = { seconds ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageAutoDismissSeconds(seconds)
                }
            },
            onFloatIconSizeDpChange = { sizeDp ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setMessageFloatIconSizeDp(sizeDp)
                }
            },
        )
    }

    entry<AppNavKey.OtpHub> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        var officialRules by remember {
            mutableStateOf(ctx.app.otpOfficialRulesLoader.getRules())
        }
        OtpHubScreen(
            settings = settings,
            officialRules = officialRules,
            accessibilityGranted = permissions.accessibilityGranted,
            onExit = { ctx.navigateBackTo(AppNavKey.NotificationHub) },
            onCopyToClipboardChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpCopyToClipboard(enabled)
                }
            },
            onKeywordsRegexChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpKeywordsRegex(value)
                }
            },
            onRefreshOfficialRules = {
                officialRules = ctx.app.otpOfficialRulesLoader.refresh()
            },
            onOfficialRuleEnabledChange = { ruleId, enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpOfficialRuleEnabled(ruleId, enabled)
                }
            },
            onUserRulesChange = { rules ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpUserMatchRules(rules)
                }
            },
            onAutoInputChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpAutoInputEnabled(enabled)
                }
            },
            onAutoConfirmChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpAutoConfirmEnabled(enabled)
                }
            },
            onAccessibilityAssistChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpAccessibilityAssistEnabled(enabled)
                }
            },
            onDelayChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpAutoInputDelayMs(value)
                }
            },
            onIntervalChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpAutoInputIntervalMs(value)
                }
            },
            onRequestAccessibility = { ctx.openAccessibilitySettings() },
        )
    }

    entry<AppNavKey.OtpSettings> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        val officialRules = remember { ctx.app.otpOfficialRulesLoader.getRules() }
        OtpSettingsScreen(
            settings = settings,
            officialRules = officialRules,
            onBack = { ctx.navigateBackTo(AppNavKey.NotificationHub) },
            onOpenAutoInput = { ctx.navigate(AppNavKey.OtpAutoInput) },
            onOpenMatchRules = { ctx.navigate(AppNavKey.OtpRulesList) },
            onOpenRecords = {
                ctx.navigate(AppNavKey.OtpRecords(OtpRecordsReturn.Settings))
            },
            onCopyToClipboardChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpCopyToClipboard(enabled)
                }
            },
            onKeywordsRegexChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpKeywordsRegex(value)
                }
            },
        )
    }

    entry<AppNavKey.OtpRecords> { key ->
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        OtpRecordsScreen(
            onBack = {
                ctx.navigateBackTo(
                    when (key.returnTo) {
                        OtpRecordsReturn.Hub -> AppNavKey.NotificationHub
                        OtpRecordsReturn.Settings -> AppNavKey.OtpSettings
                    },
                )
            },
            onOpenTestFlow = { ctx.navigate(AppNavKey.OtpHub) },
        )
    }

    entry<AppNavKey.OtpRulesList> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        var officialRules by remember {
            mutableStateOf(ctx.app.otpOfficialRulesLoader.getRules())
        }
        OtpRulesListScreen(
            officialRules = officialRules,
            userRules = settings.otpUserMatchRules,
            disabledOfficialRuleIds = settings.otpDisabledOfficialRuleIds,
            onBack = { ctx.navigateBackTo(AppNavKey.OtpSettings) },
            onRefreshOfficialRules = {
                officialRules = ctx.app.otpOfficialRulesLoader.refresh()
            },
            onOfficialRuleEnabledChange = { ruleId, enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpOfficialRuleEnabled(ruleId, enabled)
                }
            },
            onUserRulesChange = { rules ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpUserMatchRules(rules)
                }
            },
        )
    }

    entry<AppNavKey.OtpAutoInput> {
        val settings = ctx.collectAppSettings()
        val permissions = ctx.collectPermissions()
        OtpAutoInputSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.OtpSettings) },
            onRequestAccessibility = { ctx.openAccessibilitySettings() },
            onAccessibilityAssistChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpAccessibilityAssistEnabled(enabled)
                }
            },
            onAutoInputChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpAutoInputEnabled(enabled)
                }
            },
            onAutoConfirmChange = { enabled ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpAutoConfirmEnabled(enabled)
                }
            },
            onDelayChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpAutoInputDelayMs(value)
                }
            },
            onIntervalChange = { value ->
                ctx.launchSettingsChange {
                    ctx.app.settingsRepository.setOtpAutoInputIntervalMs(value)
                }
            },
        )
    }
}
