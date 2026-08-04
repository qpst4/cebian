package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import com.slideindex.app.otp.OtpAccessibilitySettingsHelper
import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageSettingsCodec
import com.slideindex.app.ui.MessageAppFilterEditorScreen
import com.slideindex.app.ui.MessageGestureActionPickerScreen
import com.slideindex.app.ui.MessageReminderAllowedAppsScreen
import com.slideindex.app.ui.MessageReminderDndAppsScreen
import com.slideindex.app.ui.MessageReminderSettingsScreen
import com.slideindex.app.ui.MessageStyleDetailSettingsScreen
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.SideBubbleHorizontalEdge
import com.slideindex.app.message.SideBubbleVerticalAnchor
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.ui.NotificationFilterSettingsScreen
import com.slideindex.app.ui.NotificationRuleEditorScreen
import com.slideindex.app.ui.NotificationRulesScreen
import com.slideindex.app.ui.messagestyle.SideBubbleCountPickerScreen
import com.slideindex.app.ui.NotificationHistoryScreen
import com.slideindex.app.ui.NotificationHubScreen
import com.slideindex.app.ui.OtpAutoFillStatsScreen
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
import com.slideindex.app.settings.toMinimalAppSettings
import kotlinx.coroutines.launch

fun EntryProviderScope<AppNavKey>.notificationNavEntries(ctx: MainNavContext) {
    entry<AppNavKey.NotificationHub> {
        val permissions = ctx.collectPermissions()
        val viewModel: NotificationHubViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        val visibleHistoryCount by viewModel.visibleHistoryCount.collectAsStateWithLifecycle()
        NotificationHubScreen(
            notificationListenerEnabled = permissions.notificationListenerEnabled,
            messageReminderEnabled = messageSettings.enabled &&
                messageSettings.hasAnyStyleEnabled(),
            messageReminderSettings = messageSettings,
            notificationHistoryCount = visibleHistoryCount,
            onOpenNotificationHistory = { ctx.navigate(AppNavKey.NotificationHistory) },
            onOpenOtpHub = { ctx.navigate(AppNavKey.OtpHub) },
            onOpenMessageReminder = { ctx.navigate(AppNavKey.MessageReminder) },
            bottomContentPadding = ctx.rootBottomContentPadding,
            bottomNavReselectCount = ctx.bottomNavReselectCount,
        )
    }

    entry<AppNavKey.NotificationHistory> {
        val viewModel: NotificationHistoryViewModel = hiltViewModel()
        val permissions = ctx.collectPermissions()
        NotificationHistoryScreen(
            viewModel = viewModel,
            listenerEnabled = permissions.notificationListenerEnabled,
            onBack = { ctx.navigateBackTo(AppNavKey.NotificationHub) },
            onOpenRules = { ctx.navigate(AppNavKey.NotificationFilterRules) },
            onOpenSettings = { ctx.navigate(AppNavKey.NotificationFilterSettings) },
            onRequestListenerAccess = { ctx.openNotificationListenerSettings() },
        )
    }

    entry<AppNavKey.NotificationFilterRules> {
        val viewModel: NotificationHistoryViewModel = hiltViewModel()
        val filterRules by viewModel.rules.collectAsStateWithLifecycle()
        NotificationRulesScreen(
            rules = filterRules.filter { it.userCreated },
            viewModel = viewModel,
            onBack = { ctx.navigateBackTo(AppNavKey.NotificationHistory) },
            onRemoveRule = viewModel::removeRule,
            onSetRuleEnabled = viewModel::setRuleEnabled,
            onOpenRuleEditor = { ruleId ->
                ctx.navigate(AppNavKey.NotificationFilterRuleEditor(ruleId.orEmpty()))
            },
        )
    }

    entry<AppNavKey.NotificationFilterRuleEditor> { key ->
        val viewModel: NotificationHistoryViewModel = hiltViewModel()
        val filterRules by viewModel.rules.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val initialRule = key.ruleId.takeIf { it.isNotEmpty() }
            ?.let { id -> filterRules.find { it.id == id } }
        NotificationRuleEditorScreen(
            initialRule = initialRule,
            viewModel = viewModel,
            onBack = { ctx.navigateBackTo(AppNavKey.NotificationFilterRules) },
            onSave = { saved ->
                if (saved.actionEntries.isEmpty()) {
                    Toast.makeText(context, R.string.notification_rule_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.upsertRule(saved)
                    Toast.makeText(context, R.string.notification_rule_saved, Toast.LENGTH_SHORT).show()
                    ctx.navigateBackTo(AppNavKey.NotificationFilterRules)
                }
            },
        )
    }

    entry<AppNavKey.NotificationFilterSettings> {
        val viewModel: NotificationHistoryViewModel = hiltViewModel()
        val permissions = ctx.collectPermissions()
        NotificationFilterSettingsScreen(
            viewModel = viewModel,
            listenerEnabled = permissions.notificationListenerEnabled,
            onBack = { ctx.navigateBackTo(AppNavKey.NotificationHistory) },
            onRequestListenerAccess = { ctx.openNotificationListenerSettings() },
        )
    }

    entry<AppNavKey.MessageReminder> {
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        MessageReminderSettingsScreen(
            settings = messageSettings,
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
            onOpenGestureActionPick = { slot ->
                ctx.navigate(AppNavKey.MessageReminderGestureActionPick(slot))
            },
            onOpenAllowedApps = { ctx.navigate(AppNavKey.MessageReminderAllowedApps) },
            onOpenDndApps = { ctx.navigate(AppNavKey.MessageReminderDndApps) },
            onSuppressWhenSystemDndChange = viewModel::setMessageSuppressWhenSystemDnd,
            onOpenOverlayPermission = { ctx.openOverlaySettings() },
            onOpenNotificationListenerPermission = { ctx.openNotificationListenerSettings() },
        )
    }

    entry<AppNavKey.MessageStyleDetail> { key ->
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        MessageStyleDetailSettingsScreen(
            style = MessageStyle.fromId(key.styleId),
            settings = messageSettings,
            bottomContentPadding = ctx.rootBottomContentPadding,
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminder) },
            onOpenSideCountPick = {
                ctx.navigate(AppNavKey.MessageStyleSideBubbleCount)
            },
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

    entry<AppNavKey.MessageStyleSideBubbleCount> {
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        val returnKey = AppNavKey.MessageStyleDetail(MessageStyle.SideBubble.id)
        SideBubbleCountPickerScreen(
            selectedCount = messageSettings.sideMaxCount,
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelect = { count ->
                viewModel.setMessageSideMaxCount(count)
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    entry<AppNavKey.MessageReminderAllowedApps> {
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        MessageReminderAllowedAppsScreen(
            settings = messageSettings,
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminder) },
            onAddPackage = viewModel::addMessageEnabledPackage,
            onRemovePackage = viewModel::removeMessageEnabledPackage,
            onSaveFilterRule = viewModel::upsertMessageAppFilterRule,
            onOpenFilterEditor = { packageName ->
                ctx.navigate(AppNavKey.MessageReminderAppFilterEdit(packageName))
            },
        )
    }

    entry<AppNavKey.MessageReminderAppFilterEdit> { key ->
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        val packageName = key.packageName
        val appLabel = remember(packageName) {
            runCatching {
                ctx.activity.packageManager.getApplicationLabel(
                    ctx.activity.packageManager.getApplicationInfo(packageName, 0),
                ).toString()
            }.getOrDefault(packageName)
        }
        MessageAppFilterEditorScreen(
            appLabel = appLabel,
            rule = messageSettings.filterRuleFor(packageName),
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminderAllowedApps) },
            onSave = { rule ->
                viewModel.upsertMessageAppFilterRule(rule)
                ctx.navigateBackTo(AppNavKey.MessageReminderAllowedApps)
            },
        )
    }

    entry<AppNavKey.MessageReminderGestureActionPick> { key ->
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        val current = when (key.slot) {
            MessageSettingsCodec.SLOT_TAP -> messageSettings.singleTapAction
            MessageSettingsCodec.SLOT_UP -> messageSettings.swipeUpAction
            MessageSettingsCodec.SLOT_DOWN -> messageSettings.swipeDownAction
            MessageSettingsCodec.SLOT_LEFT -> messageSettings.swipeLeftAction
            MessageSettingsCodec.SLOT_RIGHT -> messageSettings.swipeRightAction
            MessageSettingsCodec.SLOT_LONG_PRESS -> messageSettings.longPressAction
            else -> MessageAction.Ignore
        }
        MessageGestureActionPickerScreen(
            current = current,
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminder) },
            onSelect = { action ->
                viewModel.setMessageGestureAction(key.slot, action)
                ctx.navigateBackTo(AppNavKey.MessageReminder)
            },
        )
    }

    entry<AppNavKey.MessageReminderDndApps> {
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        MessageReminderDndAppsScreen(
            dndPackages = messageSettings.dndPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminder) },
            onAddPackage = viewModel::addMessageDndPackage,
            onRemovePackage = viewModel::removeMessageDndPackage,
        )
    }

    entry<AppNavKey.OtpHub> {
        val viewModel: OtpSettingsViewModel = hiltViewModel()
        val statsViewModel: OtpAutoFillStatsViewModel = hiltViewModel()
        val otpSettings by viewModel.otpUiSettings.collectAsStateWithLifecycle()
        val settings = otpSettings.toMinimalAppSettings()
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
            onOpenStats = { ctx.navigate(AppNavKey.OtpAutoFillStats(OtpAutoFillStatsReturn.Hub)) },
        )
    }

    entry<AppNavKey.OtpSettings> {
        val viewModel: OtpSettingsViewModel = hiltViewModel()
        val otpSettings by viewModel.otpUiSettings.collectAsStateWithLifecycle()
        val settings = otpSettings.toMinimalAppSettings()
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
        val otpSettings by viewModel.otpUiSettings.collectAsStateWithLifecycle()
        val officialRules by viewModel.officialRules.collectAsStateWithLifecycle()
        OtpRulesListScreen(
            officialRules = officialRules,
            userRules = otpSettings.otpUserMatchRules,
            disabledOfficialRuleIds = otpSettings.otpDisabledOfficialRuleIds,
            onBack = { ctx.navigateBackTo(AppNavKey.OtpSettings) },
            onRefreshOfficialRules = viewModel::refreshOfficialRules,
            onOfficialRuleEnabledChange = viewModel::setOtpOfficialRuleEnabled,
            onUserRulesChange = viewModel::setOtpUserMatchRules,
        )
    }

    entry<AppNavKey.OtpAutoInput> {
        val viewModel: OtpSettingsViewModel = hiltViewModel()
        val statsViewModel: OtpAutoFillStatsViewModel = hiltViewModel()
        val otpSettings by viewModel.otpUiSettings.collectAsStateWithLifecycle()
        val settings = otpSettings.toMinimalAppSettings()
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
            onOpenStats = { ctx.navigate(AppNavKey.OtpAutoFillStats(OtpAutoFillStatsReturn.AutoInput)) },
        )
    }

    entry<AppNavKey.OtpAutoFillStats> { key ->
        val statsViewModel: OtpAutoFillStatsViewModel = hiltViewModel()
        val stats by statsViewModel.stats.collectAsStateWithLifecycle()
        OtpAutoFillStatsScreen(
            stats = stats,
            onBack = {
                ctx.navigateBackTo(
                    when (key.returnTo) {
                        OtpAutoFillStatsReturn.Hub -> AppNavKey.OtpHub
                        OtpAutoFillStatsReturn.AutoInput -> AppNavKey.OtpAutoInput
                    },
                )
            },
            onResetStats = statsViewModel::resetStats,
        )
    }
}
