package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixHubScaffold
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationHubScreen(
    notificationListenerEnabled: Boolean,
    messageReminderEnabled: Boolean,
    messageReminderSettings: com.slideindex.app.message.MessageSettings,
    notificationHistoryCount: Int,
    onOpenNotificationHistory: () -> Unit,
    onOpenOtpHub: () -> Unit,
    onOpenMessageReminder: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
    bottomNavReselectCount: Int = 0,
) {
    val listState = rememberLazyListState()
    BottomNavReselectScrollEffect(
        reselectCount = bottomNavReselectCount,
        listState = listState,
    )

    val messageReminderTitle = stringResource(R.string.message_reminder_title)
    val toolsTitle = stringResource(R.string.notification_hub_section_tools)

    MiuixHubScaffold(
        title = stringResource(R.string.main_nav_notification),
        subtitle = stringResource(R.string.notification_hub_subtitle),
        modifier = Modifier.fillMaxSize(),
        listState = listState,
        bottomContentPadding = bottomContentPadding,
    ) {
        settingsLazySmallTitle(key = "message_reminder_section", title = messageReminderTitle)
        groupedCardItems(
            keyPrefix = "notification_message_reminder",
            items = buildList {
                add(
                    settingsCardScopeItem("message-reminder") {
                        MessageReminderEntryCard(
                            enabled = messageReminderEnabled,
                            settings = messageReminderSettings,
                            outlinedLeadingIcons = true,
                            onClick = onOpenMessageReminder,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "tools_section", title = toolsTitle, sectionTop = true)
        groupedCardItems(
            keyPrefix = "notification_tools",
            items = buildList {
                add(
                    settingsCardScopeItem("notification-history") {
                        NotificationHistoryEntryCard(
                            itemCount = notificationHistoryCount,
                            listenerEnabled = notificationListenerEnabled,
                            outlinedLeadingIcons = true,
                            onClick = onOpenNotificationHistory,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("otp-hub") {
                        OtpHubEntryCard(
                            outlinedLeadingIcons = true,
                            onClick = onOpenOtpHub,
                        )
                    },
                )
            },
        )
    }
}
