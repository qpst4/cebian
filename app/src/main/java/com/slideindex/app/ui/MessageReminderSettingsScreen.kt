@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SwipeDown
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material.icons.filled.SwipeUp
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@Composable
fun MessageReminderSettingsScreen(
    settings: MessageSettings,
    notificationListenerEnabled: Boolean,
    bottomContentPadding: Dp = 0.dp,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onInterceptNotificationsChange: (Boolean) -> Unit,
    onFloatIconEnabledChange: (Boolean) -> Unit,
    onSideBubbleEnabledChange: (Boolean) -> Unit,
    onDanmakuEnabledChange: (Boolean) -> Unit,
    onOpenFloatIconSettings: () -> Unit,
    onOpenSideBubbleSettings: () -> Unit,
    onOpenDanmakuSettings: () -> Unit,
    onHideInLandscapeChange: (Boolean) -> Unit,
    onPortraitDanmakuChange: (Boolean) -> Unit,
    onLandscapeDanmakuChange: (Boolean) -> Unit,
    onGestureActionChange: (String, MessageAction) -> Unit,
    onOpenAllowedApps: () -> Unit,
    onOpenDndApps: () -> Unit,
    onSuppressWhenSystemDndChange: (Boolean) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onOpenNotificationListenerPermission: () -> Unit,
) {
    val context = LocalContext.current
    val overlayPermissionGranted = PermissionHelper.canDrawOverlays(context)
    val overlayReady = MessageOverlayHost.canShow(context)
    var pickingGestureSlot by remember { mutableStateOf<String?>(null) }
    val controlsEnabled = settings.enabled

    SettingsScreenScaffold(
        title = stringResource(R.string.message_reminder_title),
        subtitle = stringResource(R.string.message_reminder_subtitle),
        onBack = onBack,
    ) {
        if (!notificationListenerEnabled || !overlayPermissionGranted) {
            if (!notificationListenerEnabled) {
                SettingsHintText(stringResource(R.string.message_reminder_permission_listener_desc))
                SettingLinkRow(
                    title = stringResource(R.string.message_reminder_permission_listener_title),
                    subtitle = stringResource(R.string.grant_permission),
                    onClick = onOpenNotificationListenerPermission,
                )
            }
            if (!overlayPermissionGranted) {
                SettingsHintText(stringResource(R.string.message_reminder_permission_overlay_desc))
                SettingLinkRow(
                    title = stringResource(R.string.permission_overlay_title),
                    subtitle = stringResource(R.string.grant_permission),
                    onClick = onOpenOverlayPermission,
                )
            }
        } else if (!overlayReady) {
            SettingsHintText(stringResource(R.string.message_reminder_permission_overlay_desc))
        }

        if (notificationListenerEnabled && overlayPermissionGranted && !settings.enabled) {
            SettingsHintText(stringResource(R.string.message_reminder_enable_hint))
        }

        SettingsSectionTitle(stringResource(R.string.message_reminder_section_general))
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.message_reminder_enabled),
                subtitle = stringResource(R.string.message_reminder_enabled_desc),
                icon = { label -> Icon(Icons.Default.Notifications, contentDescription = label) },
                checked = settings.enabled,
                enabled = notificationListenerEnabled && overlayPermissionGranted,
                onCheckedChange = onEnabledChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.message_reminder_intercept_notifications),
                subtitle = stringResource(R.string.message_reminder_intercept_notifications_desc),
                icon = { label -> Icon(Icons.Default.DoNotDisturbOn, contentDescription = label) },
                checked = settings.interceptNotifications,
                enabled = notificationListenerEnabled,
                onCheckedChange = onInterceptNotificationsChange,
            )
        }

        SettingsSectionTitle(stringResource(R.string.message_style_title))
        SettingsCard {
            SettingSwitchNavigationRow(
                title = messageStyleLabel(MessageStyle.FloatIcon),
                subtitle = stringResource(R.string.message_style_float_icon_desc),
                icon = { label ->
                    MessageReminderColoredIcon(
                        icon = Icons.Default.Notifications,
                        background = Color(0xFF42A5F5),
                        contentDescription = label,
                    )
                },
                checked = settings.floatIconEnabled,
                enabled = controlsEnabled,
                onCheckedChange = onFloatIconEnabledChange,
                onNavigate = onOpenFloatIconSettings,
            )
            SettingSwitchNavigationRow(
                title = messageStyleLabel(MessageStyle.SideBubble),
                subtitle = stringResource(R.string.message_style_side_bubble_desc),
                icon = { label ->
                    MessageReminderColoredIcon(
                        icon = Icons.Default.CropSquare,
                        background = Color(0xFF5C6BC0),
                        contentDescription = label,
                    )
                },
                checked = settings.sideBubbleEnabled,
                enabled = controlsEnabled,
                onCheckedChange = onSideBubbleEnabledChange,
                onNavigate = onOpenSideBubbleSettings,
            )
            SettingSwitchNavigationRow(
                title = messageStyleLabel(MessageStyle.Danmaku),
                subtitle = stringResource(R.string.message_style_danmaku_desc),
                icon = { label ->
                    MessageReminderColoredIcon(
                        icon = Icons.Default.SwipeLeft,
                        background = Color(0xFF26A69A),
                        contentDescription = label,
                    )
                },
                checked = settings.danmakuEnabled,
                enabled = controlsEnabled,
                onCheckedChange = onDanmakuEnabledChange,
                onNavigate = onOpenDanmakuSettings,
            )
        }

        SettingsSectionTitle(stringResource(R.string.message_reminder_section_filter))
        SettingsCard {
            SettingNavigationRow(
                icon = { label ->
                    MessageReminderColoredIcon(
                        icon = Icons.Default.Checklist,
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
            SettingNavigationRow(
                icon = { label ->
                    MessageReminderColoredIcon(
                        icon = Icons.Default.DoNotDisturbOn,
                        background = Color(0xFFF44336),
                        contentDescription = label,
                    )
                },
                title = stringResource(R.string.message_reminder_dnd_apps),
                subtitle = stringResource(R.string.message_reminder_dnd_apps_subtitle),
                enabled = settings.enabled,
                onClick = onOpenDndApps,
            )
            SettingSwitchRow(
                title = stringResource(R.string.message_reminder_suppress_system_dnd),
                subtitle = stringResource(R.string.message_reminder_suppress_system_dnd_desc),
                icon = { label ->
                    MessageReminderColoredIcon(
                        icon = Icons.Default.Bedtime,
                        background = Color(0xFF5C6BC0),
                        contentDescription = label,
                    )
                },
                checked = settings.suppressWhenSystemDnd,
                enabled = settings.enabled,
                onCheckedChange = onSuppressWhenSystemDndChange,
            )
        }

        SettingsSectionTitle(stringResource(R.string.message_reminder_section_landscape))
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.message_reminder_hide_in_landscape),
                subtitle = stringResource(R.string.message_reminder_hide_in_landscape_desc),
                checked = settings.hideInLandscape,
                enabled = settings.enabled,
                onCheckedChange = onHideInLandscapeChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.message_reminder_portrait_danmaku),
                subtitle = stringResource(R.string.message_reminder_portrait_danmaku_desc),
                checked = settings.portraitDanmaku,
                enabled = settings.enabled,
                onCheckedChange = onPortraitDanmakuChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.message_reminder_landscape_danmaku),
                subtitle = stringResource(R.string.message_reminder_landscape_danmaku_desc),
                checked = settings.landscapeDanmaku,
                enabled = settings.enabled,
                onCheckedChange = onLandscapeDanmakuChange,
            )
        }

        SettingsSectionTitle(stringResource(R.string.message_reminder_section_gestures))
        SettingsCard {
            MessageGestureActionRow(
                title = stringResource(R.string.message_reminder_gesture_tap),
                icon = Icons.Default.TouchApp,
                action = settings.singleTapAction,
                enabled = settings.enabled,
                onClick = { pickingGestureSlot = MessageSettingsCodec.SLOT_TAP },
            )
            MessageGestureActionRow(
                title = stringResource(R.string.message_reminder_gesture_swipe_up),
                icon = Icons.Default.SwipeUp,
                action = settings.swipeUpAction,
                enabled = settings.enabled,
                onClick = { pickingGestureSlot = MessageSettingsCodec.SLOT_UP },
            )
            MessageGestureActionRow(
                title = stringResource(R.string.message_reminder_gesture_swipe_down),
                icon = Icons.Default.SwipeDown,
                action = settings.swipeDownAction,
                enabled = settings.enabled,
                onClick = { pickingGestureSlot = MessageSettingsCodec.SLOT_DOWN },
            )
            MessageGestureActionRow(
                title = stringResource(R.string.message_reminder_gesture_swipe_left),
                icon = Icons.Default.SwipeLeft,
                action = settings.swipeLeftAction,
                enabled = settings.enabled,
                onClick = { pickingGestureSlot = MessageSettingsCodec.SLOT_LEFT },
            )
            MessageGestureActionRow(
                title = stringResource(R.string.message_reminder_gesture_swipe_right),
                icon = Icons.Default.SwipeRight,
                action = settings.swipeRightAction,
                enabled = settings.enabled,
                onClick = { pickingGestureSlot = MessageSettingsCodec.SLOT_RIGHT },
            )
            MessageGestureActionRow(
                title = stringResource(R.string.message_reminder_gesture_long_press),
                icon = Icons.Default.BackHand,
                action = settings.longPressAction,
                enabled = settings.enabled,
                onClick = { pickingGestureSlot = MessageSettingsCodec.SLOT_LONG_PRESS },
            )
        }

        Spacer(modifier = Modifier.height(8.dp + bottomContentPadding))
    }

    AnimatedFullScreenOverlay(visible = pickingGestureSlot != null) {
        pickingGestureSlot?.let { slot ->
            MessageActionPickerOverlay(
                current = gestureActionFor(settings, slot),
                onDismiss = { pickingGestureSlot = null },
                onSelect = { action ->
                    onGestureActionChange(slot, action)
                    pickingGestureSlot = null
                },
            )
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
                contentDescription = stringResource(R.string.cd_navigate_forward),
                modifier = Modifier.size(20.dp),
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
private fun MessageGestureActionRow(
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

private val messageGesturePickerActions = listOf(
    MessageAction.Read,
    MessageAction.ReadInSmallWindow,
    MessageAction.QuickReply,
    MessageAction.Ignore,
    MessageAction.IgnoreAndRemove,
    MessageAction.Dnd5Min,
)

@Composable
private fun MessageActionPickerOverlay(
    current: MessageAction,
    onDismiss: () -> Unit,
    onSelect: (MessageAction) -> Unit,
) {
    SettingsRadioPickerScreen(
        title = stringResource(R.string.message_reminder_pick_action),
        onBack = onDismiss,
    ) {
        messageGesturePickerActions.forEach { action ->
            SettingRadioRow(
                title = messageActionLabel(action),
                subtitle = messageActionSubtitle(action),
                selected = action == current,
                onClick = { onSelect(action) },
            )
        }
    }
}

@Composable
private fun gestureActionFor(settings: MessageSettings, slot: String): MessageAction =
    when (slot) {
        MessageSettingsCodec.SLOT_TAP -> settings.singleTapAction
        MessageSettingsCodec.SLOT_UP -> settings.swipeUpAction
        MessageSettingsCodec.SLOT_DOWN -> settings.swipeDownAction
        MessageSettingsCodec.SLOT_LEFT -> settings.swipeLeftAction
        MessageSettingsCodec.SLOT_RIGHT -> settings.swipeRightAction
        MessageSettingsCodec.SLOT_LONG_PRESS -> settings.longPressAction
        else -> MessageAction.Ignore
    }

@Composable
private fun messageActionLabel(action: MessageAction): String = when (action) {
    MessageAction.Read -> stringResource(R.string.message_action_read)
    MessageAction.ReadInSmallWindow -> stringResource(R.string.message_action_read_small_window)
    MessageAction.Ignore -> stringResource(R.string.message_action_ignore)
    MessageAction.IgnoreAndRemove -> stringResource(R.string.message_action_ignore_remove)
    MessageAction.Dnd5Min -> stringResource(R.string.message_action_dnd_5min)
    MessageAction.QuickReply -> stringResource(R.string.message_action_quick_reply)
}

@Composable
private fun messageActionSubtitle(action: MessageAction): String? = when (action) {
    MessageAction.ReadInSmallWindow -> stringResource(R.string.message_action_read_small_window_desc)
    MessageAction.Dnd5Min -> stringResource(R.string.message_action_dnd_5min_desc)
    MessageAction.QuickReply -> stringResource(R.string.message_action_quick_reply_desc)
    else -> null
}

@Composable
fun MessageReminderEntryCard(
    enabled: Boolean,
    settings: MessageSettings? = null,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.Notifications, contentDescription = label) },
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
