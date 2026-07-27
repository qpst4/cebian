package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.settings.components.HubTopAppBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    Scaffold(
        topBar = {
            HubTopAppBar(
                title = stringResource(R.string.main_nav_notification),
                subtitle = stringResource(R.string.notification_hub_subtitle),
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 8.dp + bottomContentPadding),
        ) {
            item(key = "section_message_reminder") {
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
            }

            item(key = "section_tools") {
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
    }
}
