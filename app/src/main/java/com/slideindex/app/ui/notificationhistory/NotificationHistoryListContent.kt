package com.slideindex.app.ui.notificationhistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.notification.ActiveNotificationEntry
import com.slideindex.app.notification.NotificationHistoryItem
import com.slideindex.app.notification.NotificationHistoryItemMeta
import com.slideindex.app.ui.SearchBar
import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import java.text.DateFormat
import java.util.Date

@Composable
internal fun rememberResolvableAppInfo(
    viewModel: NotificationHistoryViewModel,
    packageName: String,
): AppInfo? {
    var appInfo by remember(packageName) {
        mutableStateOf(viewModel.getCachedAppInfo(packageName))
    }
    LaunchedEffect(packageName) {
        if (appInfo == null) {
            appInfo = viewModel.resolveAppInfo(packageName)
        }
    }
    return appInfo
}

@Composable
internal fun ActiveNotificationsTab(
    modifier: Modifier,
    listenerEnabled: Boolean,
    activeNotifications: List<ActiveNotificationEntry>,
    itemMeta: (NotificationHistoryItem) -> NotificationHistoryItemMeta,
    dateFormat: DateFormat,
    viewModel: NotificationHistoryViewModel,
    onHideItem: (NotificationHistoryItem, String?) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!listenerEnabled) {
            item(key = "active_permission_hint") {
                Text(
                    text = stringResource(R.string.notification_history_permission_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else if (activeNotifications.isEmpty()) {
            item(key = "active_empty") {
                Text(
                    text = stringResource(R.string.notification_filter_active_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(activeNotifications, key = { it.key }) { entry ->
                val historyItem = entry.historyItem
                val displayItem = historyItem ?: entry.toHistoryItem()
                val meta = historyItem?.let(itemMeta) ?: itemMeta(displayItem)
                val appInfo = rememberResolvableAppInfo(viewModel, entry.packageName)
                NotificationHistoryRow(
                    item = displayItem,
                    appInfo = appInfo,
                    timeLabel = dateFormat.format(Date(entry.postedAtMs)),
                    isActiveInShade = false,
                    matchingRule = meta.matchingHideRule,
                    showHiddenBadge = meta.isHidden,
                    showRestoreAction = meta.isHidden,
                    showDeleteAction = historyItem != null,
                    onOpen = { viewModel.replayActive(entry) },
                    onHide = {
                        onHideItem(displayItem, historyItem?.id)
                    },
                    onUnhide = {
                        viewModel.restoreSnoozed(historyItem ?: displayItem)
                    },
                    onDelete = {
                        historyItem?.let { item -> viewModel.deleteItem(item.id) }
                    },
                )
            }
        }
    }
}

@Composable
internal fun HistoryNotificationsTab(
    modifier: Modifier,
    items: List<NotificationHistoryItem>,
    filteredItems: List<NotificationHistoryItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    activeKeys: Set<String>,
    itemMeta: (NotificationHistoryItem) -> NotificationHistoryItemMeta,
    dateFormat: DateFormat,
    viewModel: NotificationHistoryViewModel,
    onHideItem: (NotificationHistoryItem, String?) -> Unit,
    onDelete: (NotificationHistoryItem) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (items.isEmpty()) {
            item(key = "history_empty") {
                Text(
                    text = stringResource(R.string.notification_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            item(key = "search") {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    hintResId = R.string.notification_history_search_hint,
                )
            }
            if (filteredItems.isEmpty()) {
                item(key = "search_empty") {
                    Text(
                        text = stringResource(R.string.notification_history_search_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    val meta = itemMeta(item)
                    val appInfo = rememberResolvableAppInfo(viewModel, item.packageName)
                    NotificationHistoryRow(
                        item = item,
                        appInfo = appInfo,
                        timeLabel = dateFormat.format(Date(item.postedAtMs)),
                        isActiveInShade = item.notificationKey?.let { it in activeKeys } == true,
                        matchingRule = meta.matchingHideRule,
                        showHiddenBadge = meta.isHidden,
                        showRestoreAction = false,
                        onOpen = { viewModel.replay(item) },
                        onHide = { onHideItem(item, item.id) },
                        onUnhide = {},
                        onDelete = { onDelete(item) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun HiddenNotificationsTab(
    modifier: Modifier,
    hiddenItems: List<NotificationHistoryItem>,
    filteredItems: List<NotificationHistoryItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    activeKeys: Set<String>,
    itemMeta: (NotificationHistoryItem) -> NotificationHistoryItemMeta,
    dateFormat: DateFormat,
    viewModel: NotificationHistoryViewModel,
    onDelete: (NotificationHistoryItem) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (hiddenItems.isEmpty()) {
            item(key = "hidden_empty") {
                Text(
                    text = stringResource(R.string.notification_filter_hidden_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            item(key = "hidden_search") {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    hintResId = R.string.notification_history_search_hint,
                )
            }
            if (filteredItems.isEmpty()) {
                item(key = "hidden_search_empty") {
                    Text(
                        text = stringResource(R.string.notification_history_search_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    val appInfo = rememberResolvableAppInfo(viewModel, item.packageName)
                    NotificationHistoryRow(
                        item = item,
                        appInfo = appInfo,
                        timeLabel = dateFormat.format(Date(item.postedAtMs)),
                        isActiveInShade = item.notificationKey?.let { it in activeKeys } == true,
                        matchingRule = itemMeta(item).matchingHideRule,
                        showHiddenBadge = true,
                        showRestoreAction = true,
                        onOpen = { viewModel.replay(item) },
                        onHide = {},
                        onUnhide = { viewModel.restoreSnoozed(item) },
                        onDelete = { onDelete(item) },
                    )
                }
            }
        }
    }
}

internal fun ActiveNotificationEntry.toHistoryItem(): NotificationHistoryItem {
    return NotificationHistoryItem(
        packageName = packageName,
        title = title,
        text = text,
        postedAtMs = postedAtMs,
        intentUri = null,
        notificationKey = key,
    )
}
