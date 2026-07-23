package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import com.slideindex.app.otp.OtpAccessibilitySettingsHelper
import com.slideindex.app.ui.MessageReminderAllowedAppsScreen
import com.slideindex.app.ui.MessageReminderDndAppsScreen
import com.slideindex.app.ui.MessageReminderSettingsScreen
import com.slideindex.app.ui.MessageStyleDetailSettingsScreen
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.SideBubbleHorizontalEdge
import com.slideindex.app.message.SideBubbleVerticalAnchor
import com.slideindex.app.ui.NotificationHistoryScreen
import com.slideindex.app.ui.NotificationHubScreen
import com.slideindex.app.ui.OtpAutoInputSettingsScreen
import com.slideindex.app.ui.OtpHubScreen
import com.slideindex.app.ui.OtpRecordsScreen
import com.slideindex.app.ui.OtpRulesListScreen
import com.slideindex.app.ui.OtpSettingsScreen
import com.slideindex.app.ui.viewmodel.MessageSettingsViewModel
import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import com.slideindex.app.ui.viewmodel.NotificationHubViewModel
import com.slideindex.app.ui.viewmodel.OtpAutoFillStatsViewModel
import com.slideindex.app.ui.viewmodel.OtpSettingsViewModel
import kotlinx.coroutines.launch

fun EntryProviderScope<AppNavKey>.notificationNavEntries(ctx: MainNavContext) {
    entry<AppNavKey.NotificationHub> {
        val permissions = ctx.collectPermissions()
        val viewModel: NotificationHubViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val visibleHistoryCount by viewModel.visibleHistoryCount.collectAsStateWithLifecycle()
        NotificationHubScreen(
            notificationListenerEnabled = permissions.notificationListenerEnabled,
            messageReminderEnabled = settings.messageReminderSettings.enabled &&
                settings.messageReminderSettings.hasAnyStyleEnabled(),
            messageReminderSettings = settings.messageReminderSettings,
            notificationHistoryCount = visibleHistoryCount,
            onOpenNotificationHistory = { ctx.navigate(AppNavKey.NotificationHistory) },
            onOpenOtpHub = { ctx.navigate(AppNavKey.OtpHub) },
            onOpenMessageReminder = { ctx.navigate(AppNavKey.MessageReminder) },
            bottomContentPadding = ctx.rootBottomContentPadding,
        )
    }

    entry<AppNavKey.NotificationHistory> {
        val viewModel: NotificationHistoryViewModel = hiltViewModel()
        val permissions = ctx.collectPermissions()
        NotificationHistoryScreen(
            viewModel = viewModel,
            listenerEnabled = permissions.notificationListenerEnabled,
            onBack = { ctx.navigateBackTo(AppNavKey.NotificationHub) },
            onRequestListenerAccess = { ctx.openNotificationListenerSettings() },
        )
    }

    entry<AppNavKey.MessageReminder> {
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        MessageReminderSettingsScreen(
            settings = settings.messageReminderSettings,
            notificationListenerEnabled = permissions.notificationListenerEnabled,
            bottomContentPadding = ctx.rootBottomContentPadding,
            onBack = { ctx.navigateBackTo(AppNavKey.NotificationHub) },
            onEnabledChange = viewModel::setMessageReminderEnabled,
            onInterceptNotificationsChange = viewModel::setMessageInterceptNotifications,
            onFloatIconEnabledChange = viewModel::setMessageFloatIconEnabled,
            onSideBubbleEnabledChange = viewModel::setMessageSideBubbleEnabled,
            onDanmakuEnabledChange = viewModel::setMessageDanmakuEnabled,
            onOpenFloatIconSettings = {
                ctx.navigate(AppNavKey.MessageStyleDetail(MessageStyle.FloatIcon.id))
            },
            onOpenSideBubbleSettings = {
                ctx.navigate(AppNavKey.MessageStyleDetail(MessageStyle.SideBubble.id))
            },
            onOpenDanmakuSettings = {
                ctx.navigate(AppNavKey.MessageStyleDetail(MessageStyle.Danmaku.id))
            },
            onHideInLandscapeChange = viewModel::setMessageHideInLandscape,
            onPortraitDanmakuChange = viewModel::setMessagePortraitDanmaku,
            onLandscapeDanmakuChange = viewModel::setMessageLandscapeDanmaku,
            onGestureActionChange = viewModel::setMessageGestureAction,
            onOpenAllowedApps = { ctx.navigate(AppNavKey.MessageReminderAllowedApps) },
            onOpenDndApps = { ctx.navigate(AppNavKey.MessageReminderDndApps) },
            onSuppressWhenSystemDndChange = viewModel::setMessageSuppressWhenSystemDnd,
            onOpenOverlayPermission = { ctx.openOverlaySettings() },
            onOpenNotificationListenerPermission = { ctx.openNotificationListenerSettings() },
        )
    }

    entry<AppNavKey.MessageStyleDetail> { key ->
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        MessageStyleDetailSettingsScreen(
            style = MessageStyle.fromId(key.styleId),
            settings = settings.messageReminderSettings,
            bottomContentPadding = ctx.rootBottomContentPadding,
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminder) },
            onSideThemeIdChange = viewModel::setMessageSideThemeId,
            onDanmakuThemeIdChange = viewModel::setMessageDanmakuThemeId,
            onFloatIconOpacityChange = viewModel::setMessageFloatIconOpacity,
            onSideBubbleOpacityChange = viewModel::setMessageSideBubbleOpacity,
            onDanmakuOpacityChange = viewModel::setMessageDanmakuOpacity,
            onDanmakuMaxLinesChange = viewModel::setMessageDanmakuMaxLines,
            onSideMaxCountChange = viewModel::setMessageSideMaxCount,
            onSideMaxLinesChange = viewModel::setMessageSideMaxLines,
            onFloatIconSizeDpChange = viewModel::setMessageFloatIconSizeDp,
            onAutoDismissSecondsChange = viewModel::setMessageAutoDismissSeconds,
            onSideHorizontalEdgeChange = { edge ->
                viewModel.setMessageSideHorizontalEdge(edge.id)
            },
            onSideVerticalAnchorChange = { anchor ->
                viewModel.setMessageSideVerticalAnchor(anchor.id)
            },
            onSideFontSizeLevelChange = viewModel::setMessageSideFontSizeLevel,
            onDanmakuSpeedLevelChange = viewModel::setMessageDanmakuSpeedLevel,
        )
    }

    entry<AppNavKey.MessageReminderAllowedApps> {
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        MessageReminderAllowedAppsScreen(
            settings = settings.messageReminderSettings,
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminder) },
            onAddPackage = viewModel::addMessageEnabledPackage,
            onRemovePackage = viewModel::removeMessageEnabledPackage,
            onSaveFilterRule = viewModel::upsertMessageAppFilterRule,
        )
    }

    entry<AppNavKey.MessageReminderDndApps> {
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        MessageReminderDndAppsScreen(
            dndPackages = settings.messageReminderSettings.dndPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminder) },
            onAddPackage = viewModel::addMessageDndPackage,
            onRemovePackage = viewModel::removeMessageDndPackage,
        )
    }

    entry<AppNavKey.OtpHub> {
        val viewModel: OtpSettingsViewModel = hiltViewModel()
        val statsViewModel: OtpAutoFillStatsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val stats by statsViewModel.stats.collectAsStateWithLifecycle()
        val officialRules by viewModel.officialRules.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val requestAccessibility: () -> Unit = {
            scope.launch {
                if (!OtpAccessibilitySettingsHelper.ensureAccessibilityEnabled(context)) {
                    ctx.openAccessibilitySettings()
                }
            }
        }
        OtpHubScreen(
            settings = settings,
            officialRules = officialRules,
            accessibilityGranted = permissions.accessibilityGranted,
            onExit = { ctx.navigateBackTo(AppNavKey.NotificationHub) },
            onCopyToClipboardChange = viewModel::setOtpCopyToClipboard,
            onKeywordsRegexChange = viewModel::setOtpKeywordsRegex,
            onRefreshOfficialRules = viewModel::refreshOfficialRules,
            onOfficialRuleEnabledChange = viewModel::setOtpOfficialRuleEnabled,
            onUserRulesChange = viewModel::setOtpUserMatchRules,
            onAutoInputChange = viewModel::setOtpAutoInputEnabled,
            onAutoConfirmChange = viewModel::setOtpAutoConfirmEnabled,
            onDelayChange = viewModel::setOtpAutoInputDelayMs,
            onIntervalChange = viewModel::setOtpAutoInputIntervalMs,
            onRequestAccessibility = requestAccessibility,
            onLsposedSmsChange = viewModel::setOtpLsposedSmsCaptureEnabled,
            onLsposedSystemInjectChange = viewModel::setOtpLsposedSystemInjectEnabled,
            stats = stats,
            onResetStats = statsViewModel::resetStats,
        )
    }

    entry<AppNavKey.OtpSettings> {
        val viewModel: OtpSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val officialRules by viewModel.officialRules.collectAsStateWithLifecycle()
        OtpSettingsScreen(
            settings = settings,
            officialRules = officialRules,
            onBack = { ctx.navigateBackTo(AppNavKey.NotificationHub) },
            onOpenAutoInput = { ctx.navigate(AppNavKey.OtpAutoInput) },
            onOpenMatchRules = { ctx.navigate(AppNavKey.OtpRulesList) },
            onOpenRecords = {
                ctx.navigate(AppNavKey.OtpRecords(OtpRecordsReturn.Settings))
            },
            onKeywordsRegexChange = viewModel::setOtpKeywordsRegex,
        )
    }

    entry<AppNavKey.OtpRecords> { key ->
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
        val viewModel: OtpSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val officialRules by viewModel.officialRules.collectAsStateWithLifecycle()
        OtpRulesListScreen(
            officialRules = officialRules,
            userRules = settings.otpUserMatchRules,
            disabledOfficialRuleIds = settings.otpDisabledOfficialRuleIds,
            onBack = { ctx.navigateBackTo(AppNavKey.OtpSettings) },
            onRefreshOfficialRules = viewModel::refreshOfficialRules,
            onOfficialRuleEnabledChange = viewModel::setOtpOfficialRuleEnabled,
            onUserRulesChange = viewModel::setOtpUserMatchRules,
        )
    }

    entry<AppNavKey.OtpAutoInput> {
        val viewModel: OtpSettingsViewModel = hiltViewModel()
        val statsViewModel: OtpAutoFillStatsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val stats by statsViewModel.stats.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val requestAccessibility: () -> Unit = {
            scope.launch {
                if (!OtpAccessibilitySettingsHelper.ensureAccessibilityEnabled(context)) {
                    ctx.openAccessibilitySettings()
                }
            }
        }
        OtpAutoInputSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.OtpSettings) },
            onRequestAccessibility = requestAccessibility,
            onAutoInputChange = viewModel::setOtpAutoInputEnabled,
            onAutoConfirmChange = viewModel::setOtpAutoConfirmEnabled,
            onDelayChange = viewModel::setOtpAutoInputDelayMs,
            onIntervalChange = viewModel::setOtpAutoInputIntervalMs,
            onLsposedSmsChange = viewModel::setOtpLsposedSmsCaptureEnabled,
            onLsposedSystemInjectChange = viewModel::setOtpLsposedSystemInjectEnabled,
            onCopyToClipboardChange = viewModel::setOtpCopyToClipboard,
            stats = stats,
            onResetStats = statsViewModel::resetStats,
        )
    }
}
