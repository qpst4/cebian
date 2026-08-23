package com.slideindex.app.ui.notificationhistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour

internal enum class NotificationFilterTab {
    ACTIVE,
    HISTORY,
    HIDDEN,
}

@Composable
internal fun NotificationHistoryTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    MiuixTabRowWithContour(
        tabs = listOf(
            stringResource(R.string.notification_filter_tab_active),
            stringResource(R.string.notification_filter_tab_history),
            stringResource(R.string.notification_filter_history_hidden),
        ),
        selectedTabIndex = selectedTab,
        onTabSelected = onTabSelected,
    )
}

internal fun LazyListScope.notificationHistoryFilterBarItems(
    listenerEnabled: Boolean,
    onGrantListenerAccess: () -> Unit,
) {
    if (!listenerEnabled) {
        item(key = "permission_card") {
            NotificationHistoryPermissionCard(
                onGrant = onGrantListenerAccess,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
internal fun NotificationHistoryPermissionCard(
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.errorContainer,
            contentColor = MiuixTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.notification_history_permission_title),
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.notification_history_permission_desc),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onErrorContainer,
            )
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.notification_history_permission_grant))
            }
        }
    }
}
