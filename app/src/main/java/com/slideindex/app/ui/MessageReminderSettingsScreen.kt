@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BackHand
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SwipeDown
import androidx.compose.material.icons.outlined.SwipeLeft
import androidx.compose.material.icons.outlined.SwipeRight
import androidx.compose.material.icons.outlined.SwipeUp
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageSettingsCodec
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.overlay.MessageOverlayHost
import com.slideindex.app.ui.messagestyle.messageStyleLabel
import com.slideindex.app.ui.messagestyle.messageStyleSummary
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_01
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@Composable
fun MessageReminderSettingsScreen(
    settings: MessageSettings,
    notificationListenerEnabled: Boolean,
    bottomContentPadding: Dp = 0.dp,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onInterceptNotificationsChange: (Boolean) -> Unit,
    onOpenLastMessageOnUnlockChange: (Boolean) -> Unit,
    onUnlockConfirmationAutoDismissSecondsChange: (Int) -> Unit,
    onOpenLastMessageOnUnlockRules: () -> Unit,
    onFloatIconEnabledChange: (Boolean) -> Unit,
    onFloatIconSizeDpChange: (Float) -> Unit,
    onFloatIconOpacityChange: (Float) -> Unit,
    onSideBubbleEnabledChange: (Boolean) -> Unit,
    onDanmakuEnabledChange: (Boolean) -> Unit,
    onOpenFloatIconSettings: () -> Unit,
    onOpenSideBubbleSettings: () -> Unit,
    onOpenDanmakuSettings: () -> Unit,
    onHideInLandscapeChange: (Boolean) -> Unit,
    onPortraitDanmakuChange: (Boolean) -> Unit,
    onLandscapeDanmakuChange: (Boolean) -> Unit,
    onGestureActionChange: (String, MessageAction) -> Unit,
    onOpenGestureActionPick: (String) -> Unit,
    onOpenAllowedApps: () -> Unit,
    onOpenDndApps: () -> Unit,
    onSuppressWhenSystemDndChange: (Boolean) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onOpenNotificationListenerPermission: () -> Unit,
) {
    val context = LocalContext.current
    val overlayPermissionGranted = PermissionHelper.canDrawOverlays(context)
    val overlayReady = MessageOverlayHost.canShow(context)
    val controlsEnabled = settings.enabled

    val listenerPermHint = stringResource(R.string.message_reminder_permission_listener_desc)
    val overlayPermHint = stringResource(R.string.message_reminder_permission_overlay_desc)
    val enableHint = stringResource(R.string.message_reminder_enable_hint)
    val generalSectionTitle = stringResource(R.string.message_reminder_section_general)
    val styleSectionTitle = stringResource(R.string.message_style_title)
    val filterSectionTitle = stringResource(R.string.message_reminder_section_filter)
    val landscapeSectionTitle = stringResource(R.string.message_reminder_section_landscape)
    val gesturesSectionTitle = stringResource(R.string.message_reminder_section_gestures)

    SettingsScreenScaffold(
        title = stringResource(R.string.message_reminder_title),
        subtitle = stringResource(R.string.message_reminder_subtitle),
        onBack = onBack,
    ) {
        if (!notificationListenerEnabled || !overlayPermissionGranted) {
            if (!notificationListenerEnabled) {
                settingsLazyHint(
                    key = "message-perm-listener-hint",
                    text = listenerPermHint,
                )
            }
            if (!overlayPermissionGranted) {
                settingsLazyHint(
                    key = "message-perm-overlay-hint",
                    text = overlayPermHint,
                )
            }
            groupedCardItems(
                keyPrefix = "message-reminder-permissions",
                items = buildList {
                    if (!notificationListenerEnabled) {
                        add(
                            settingsCardScopeItem("listener-permission") {
                                SettingLinkRow(
                                    title = stringResource(R.string.message_reminder_permission_listener_title),
                                    subtitle = stringResource(R.string.grant_permission),
                                    onClick = onOpenNotificationListenerPermission,
                                )
                            },
                        )
                    }
                    if (!overlayPermissionGranted) {
                        add(
                            settingsCardScopeItem("overlay-permission") {
                                SettingLinkRow(
                                    title = stringResource(R.string.permission_overlay_title),
                                    subtitle = stringResource(R.string.grant_permission),
                                    onClick = onOpenOverlayPermission,
                                )
                            },
                        )
                    }
                },
            )
        } else if (!overlayReady) {
            settingsLazyHint(
                key = "message-overlay-not-ready",
                text = overlayPermHint,
            )
        }

        if (notificationListenerEnabled && overlayPermissionGranted && !settings.enabled) {
            settingsLazyHint(
                key = "message-enable-hint",
                text = enableHint,
            )
        }

        settingsLazySmallTitle(
            key = "message-general-section",
            title = generalSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "message-general",
            items = buildList {
                add(
                    settingsCardScopeItem("enabled") {
                        SettingSwitchRow(
                            title = stringResource(R.string.message_reminder_enabled),
                            subtitle = stringResource(R.string.message_reminder_enabled_desc),
                            icon = { label -> Icon(Icons.Outlined.Notifications, contentDescription = label) },
                            checked = settings.enabled,
                            enabled = notificationListenerEnabled && overlayPermissionGranted,
                            onCheckedChange = onEnabledChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("intercept-notifications") {
                        SettingSwitchRow(
                            title = stringResource(R.string.message_reminder_intercept_notifications),
                            subtitle = stringResource(R.string.message_reminder_intercept_notifications_desc),
                            icon = { label -> Icon(Icons.Outlined.DoNotDisturbOn, contentDescription = label) },
                            checked = settings.interceptNotifications,
                            enabled = notificationListenerEnabled,
                            onCheckedChange = onInterceptNotificationsChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("open-last-on-unlock") {
                        Column {
                            SettingSwitchNavigationRow(
                                title = stringResource(R.string.message_reminder_open_last_on_unlock),
                                subtitle = stringResource(R.string.message_reminder_open_last_on_unlock_desc),
                                icon = { label -> Icon(Icons.Outlined.LockOpen, contentDescription = label) },
                                checked = settings.openLastMessageOnUnlock,
                                enabled = controlsEnabled,
                                onCheckedChange = onOpenLastMessageOnUnlockChange,
                                onNavigate = onOpenLastMessageOnUnlockRules,
                            )
                            AnimatedVisibility(
                                visible = settings.openLastMessageOnUnlock && controlsEnabled,
                                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                            ) {
                                SettingsSliderRow(
                                    title = stringResource(R.string.message_reminder_unlock_auto_dismiss),
                                    value = settings.unlockConfirmationAutoDismissSeconds.toFloat(),
                                    valueRange = 0f..30f,
                                    steps = 29,
                                    enabled = controlsEnabled,
                                    label = if (settings.unlockConfirmationAutoDismissSeconds == 0) {
                                        stringResource(R.string.message_reminder_unlock_auto_dismiss_never)
                                    } else {
                                        "${settings.unlockConfirmationAutoDismissSeconds}s"
                                    },
                                    formatLabel = { seconds ->
                                        if (seconds == 0f) "永不" else "${seconds.toInt()}s"
                                    },
                                    onValueChange = { onUnlockConfirmationAutoDismissSecondsChange(it.toInt()) },
                                )
                            }
                        }
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "message-style-section",
            title = styleSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "message-style",
            items = buildList {
                add(
                    settingsCardScopeItem("float-icon") {
                        Column {
                            SettingSwitchRow(
                                title = messageStyleLabel(MessageStyle.FloatIcon),
                                subtitle = stringResource(R.string.message_style_float_icon_desc),
                                icon = { label ->
                                    MessageReminderColoredIcon(
                                        icon = Icons.Outlined.Notifications,
                                        background = Color(0xFF42A5F5),
                                        contentDescription = label,
                                    )
                                },
                                checked = settings.floatIconEnabled,
                                enabled = controlsEnabled,
                                onCheckedChange = onFloatIconEnabledChange,
                            )
                            AnimatedVisibility(
                                visible = settings.floatIconEnabled && controlsEnabled,
                                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                            ) {
                                Column {
                                    SettingsSliderRow(
                                        title = stringResource(R.string.message_style_float_icon_size),
                                        value = settings.floatIconSizeDp,
                                        valueRange = 32f..64f,
                                        steps = 31,
                                        enabled = controlsEnabled,
                                        label = "${settings.floatIconSizeDp.toInt()} dp",
                                        formatLabel = { "${it.toInt()} dp" },
                                        onValueChange = onFloatIconSizeDpChange,
                                    )
                                    SettingsSliderRow(
                                        title = stringResource(R.string.message_style_float_icon_opacity),
                                        value = settings.floatIconOpacity,
                                        valueRange = 0f..1f,
                                        enabled = controlsEnabled,
                                        label = "${(settings.floatIconOpacity * 100).toInt()}%",
                                        formatLabel = { "${(it * 100).toInt()}%" },
                                        keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_01,
                                        onValueChange = onFloatIconOpacityChange,
                                    )
                                }
                            }
                        }
                    },
                )
                add(
                    settingsCardScopeItem("side-bubble") {
                        SettingSwitchNavigationRow(
                            title = messageStyleLabel(MessageStyle.SideBubble),
                            subtitle = stringResource(R.string.message_style_side_bubble_desc),
                            icon = { label ->
                                MessageReminderColoredIcon(
                                    icon = Icons.Outlined.CropSquare,
                                    background = Color(0xFF5C6BC0),
                                    contentDescription = label,
                                )
                            },
                            checked = settings.sideBubbleEnabled,
                            enabled = controlsEnabled,
                            onCheckedChange = onSideBubbleEnabledChange,
                            onNavigate = onOpenSideBubbleSettings,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("danmaku") {
                        SettingSwitchNavigationRow(
                            title = messageStyleLabel(MessageStyle.Danmaku),
                            subtitle = stringResource(R.string.message_style_danmaku_desc),
                            icon = { label ->
                                MessageReminderColoredIcon(
                                    icon = Icons.Outlined.SwipeLeft,
                                    background = Color(0xFF26A69A),
                                    contentDescription = label,
                                )
                            },
                            checked = settings.danmakuEnabled,
                            enabled = controlsEnabled,
                            onCheckedChange = onDanmakuEnabledChange,
                            onNavigate = onOpenDanmakuSettings,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "message-filter-section",
            title = filterSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "message-filter",
            items = buildList {
                add(
                    settingsCardScopeItem("allowed-apps") {
                        SettingNavigationRow(
                            icon = { label ->
                                MessageReminderColoredIcon(
                                    icon = Icons.Outlined.Checklist,
                                    background = Color(0xFF4CAF50),
                                    contentDescription = label,
                                )
                            },
                            title = stringResource(R.string.message_reminder_allowed_apps),
                            subtitle = stringResource(R.string.message_reminder_allowed_apps_subtitle),
                            enabled = settings.enabled,
                            onClick = onOpenAllowedApps,
                            trailingContent = {
                                MessageReminderNavigationTrailing(
                                    count = settings.enabledPackages.size,
                                    showChevron = true,
                                )
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("dnd-apps") {
                        SettingNavigationRow(
                            icon = { label ->
                                MessageReminderColoredIcon(
                                    icon = Icons.Outlined.DoNotDisturbOn,
                                    background = Color(0xFFF44336),
                                    contentDescription = label,
                                )
                            },
                            title = stringResource(R.string.message_reminder_dnd_apps),
                            subtitle = stringResource(R.string.message_reminder_dnd_apps_subtitle),
                            enabled = settings.enabled,
                            onClick = onOpenDndApps,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("suppress-system-dnd") {
                        SettingSwitchRow(
                            title = stringResource(R.string.message_reminder_suppress_system_dnd),
                            subtitle = stringResource(R.string.message_reminder_suppress_system_dnd_desc),
                            icon = { label ->
                                MessageReminderColoredIcon(
                                    icon = Icons.Outlined.Bedtime,
                                    background = Color(0xFF5C6BC0),
                                    contentDescription = label,
                                )
                            },
                            checked = settings.suppressWhenSystemDnd,
                            enabled = settings.enabled,
                            onCheckedChange = onSuppressWhenSystemDndChange,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "message-landscape-section",
            title = landscapeSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "message-landscape",
            items = buildList {
                add(
                    settingsCardScopeItem("hide-in-landscape") {
                        SettingSwitchRow(
                            title = stringResource(R.string.message_reminder_hide_in_landscape),
                            subtitle = stringResource(R.string.message_reminder_hide_in_landscape_desc),
                            checked = settings.hideInLandscape,
                            enabled = settings.enabled,
                            onCheckedChange = onHideInLandscapeChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("portrait-danmaku") {
                        SettingSwitchRow(
                            title = stringResource(R.string.message_reminder_portrait_danmaku),
                            subtitle = stringResource(R.string.message_reminder_portrait_danmaku_desc),
                            checked = settings.portraitDanmaku,
                            enabled = settings.enabled,
                            onCheckedChange = onPortraitDanmakuChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("landscape-danmaku") {
                        SettingSwitchRow(
                            title = stringResource(R.string.message_reminder_landscape_danmaku),
                            subtitle = stringResource(R.string.message_reminder_landscape_danmaku_desc),
                            checked = settings.landscapeDanmaku,
                            enabled = settings.enabled,
                            onCheckedChange = onLandscapeDanmakuChange,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "message-gestures-section",
            title = gesturesSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "message-gestures",
            items = buildList {
                add(
                    settingsCardScopeItem("gesture-tap") {
                        MessageGestureActionRow(
                            title = stringResource(R.string.message_reminder_gesture_tap),
                            icon = Icons.Outlined.TouchApp,
                            action = settings.singleTapAction,
                            enabled = settings.enabled,
                            onClick = { onOpenGestureActionPick(MessageSettingsCodec.SLOT_TAP) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("gesture-swipe-up") {
                        MessageGestureActionRow(
                            title = stringResource(R.string.message_reminder_gesture_swipe_up),
                            icon = Icons.Outlined.SwipeUp,
                            action = settings.swipeUpAction,
                            enabled = settings.enabled,
                            onClick = { onOpenGestureActionPick(MessageSettingsCodec.SLOT_UP) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("gesture-swipe-down") {
                        MessageGestureActionRow(
                            title = stringResource(R.string.message_reminder_gesture_swipe_down),
                            icon = Icons.Outlined.SwipeDown,
                            action = settings.swipeDownAction,
                            enabled = settings.enabled,
                            onClick = { onOpenGestureActionPick(MessageSettingsCodec.SLOT_DOWN) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("gesture-swipe-left") {
                        MessageGestureActionRow(
                            title = stringResource(R.string.message_reminder_gesture_swipe_left),
                            icon = Icons.Outlined.SwipeLeft,
                            action = settings.swipeLeftAction,
                            enabled = settings.enabled,
                            onClick = { onOpenGestureActionPick(MessageSettingsCodec.SLOT_LEFT) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("gesture-swipe-right") {
                        MessageGestureActionRow(
                            title = stringResource(R.string.message_reminder_gesture_swipe_right),
                            icon = Icons.Outlined.SwipeRight,
                            action = settings.swipeRightAction,
                            enabled = settings.enabled,
                            onClick = { onOpenGestureActionPick(MessageSettingsCodec.SLOT_RIGHT) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("gesture-long-press") {
                        MessageGestureActionRow(
                            title = stringResource(R.string.message_reminder_gesture_long_press),
                            icon = Icons.Outlined.BackHand,
                            action = settings.longPressAction,
                            enabled = settings.enabled,
                            onClick = { onOpenGestureActionPick(MessageSettingsCodec.SLOT_LONG_PRESS) },
                        )
                    },
                )
            },
        )

        item(key = "message-reminder-bottom-inset") {
            Spacer(modifier = Modifier.height(8.dp + bottomContentPadding))
        }
    }
}

@Composable
private fun MessageReminderNavigationTrailing(
    count: Int,
    showChevron: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (count > 0) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.cd_navigate_forward), modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageReminderColoredIcon(
    icon: ImageVector,
    background: Color,
    contentColor: Color = Color.White,
    contentDescription: String,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = background,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SettingsCardScope.MessageGestureActionRow(
    title: String,
    icon: ImageVector,
    action: MessageAction,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label -> Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary) },
        title = title,
        subtitle = messageActionLabel(action),
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.MessageReminderEntryCard(
    enabled: Boolean,
    settings: MessageSettings? = null,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label ->
            Icon(HubLeadingIcons.notifications(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.message_reminder_title),
        subtitle = if (settings != null) {
            messageStyleSummary(settings)
        } else if (enabled) {
            stringResource(R.string.message_reminder_entry_enabled)
        } else {
            stringResource(R.string.message_reminder_entry_disabled)
        },
        onClick = onClick,
    )
}
