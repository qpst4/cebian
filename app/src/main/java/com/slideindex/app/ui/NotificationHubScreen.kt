package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixHubScaffold
import com.slideindex.app.ui.settings.components.SettingsSectionTitle

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
    val scrollState = rememberScrollState()
    BottomNavReselectScrollEffect(
        reselectCount = bottomNavReselectCount,
        scrollState = scrollState,
    )

    MiuixHubScaffold(
        title = stringResource(R.string.main_nav_notification),
        subtitle = stringResource(R.string.notification_hub_subtitle),
        modifier = Modifier.fillMaxSize(),
        scrollState = scrollState,
        bottomContentPadding = bottomContentPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsSectionTitle(stringResource(R.string.message_reminder_title))
            SettingsCard {
                MessageReminderEntryCard(
                    enabled = messageReminderEnabled,
                    settings = messageReminderSettings,
                    onClick = onOpenMessageReminder,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsSectionTitle(stringResource(R.string.notification_hub_section_tools))
            SettingsCard {
                NotificationHistoryEntryCard(
                    itemCount = notificationHistoryCount,
                    listenerEnabled = notificationListenerEnabled,
                    onClick = onOpenNotificationHistory,
                )
                OtpHubEntryCard(onClick = onOpenOtpHub)
            }
        }
    }
}
