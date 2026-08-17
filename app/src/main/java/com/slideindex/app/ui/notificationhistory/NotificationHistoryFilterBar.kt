package com.slideindex.app.ui.notificationhistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.notification_history_permission_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.notification_history_permission_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onGrant) {
                Text(stringResource(R.string.notification_history_permission_grant))
            }
        }
    }
}
