package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
        MiuixSmallTitle(stringResource(R.string.message_reminder_title), modifier = Modifier.fillMaxWidth())
        SettingsCard {
            MessageReminderEntryCard(
                enabled = messageReminderEnabled,
                settings = messageReminderSettings,
                onClick = onOpenMessageReminder,
            )
        }

        MiuixSmallTitle(stringResource(R.string.notification_hub_section_tools), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
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
