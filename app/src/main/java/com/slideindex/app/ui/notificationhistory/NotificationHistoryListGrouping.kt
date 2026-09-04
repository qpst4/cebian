package com.slideindex.app.ui.notificationhistory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slideindex.app.notification.NotificationHistoryAppGroup
import com.slideindex.app.notification.NotificationHistoryListEntry
import com.slideindex.app.notification.groupNotificationHistoryByApp

private sealed interface NotificationHistoryLazyRow<T> {
    data class Single<T>(val item: T) : NotificationHistoryLazyRow<T>

    data class GroupHeader<T>(
        val group: NotificationHistoryAppGroup<T>,
    ) : NotificationHistoryLazyRow<T>

    data class GroupChild<T>(
        val packageName: String,
        val item: T,
    ) : NotificationHistoryLazyRow<T>
}

private fun <T> buildNotificationHistoryLazyRows(
    items: List<T>,
    groupByApp: Boolean,
    searchActive: Boolean,
    expandedGroups: Set<String>,
    keyOf: (T) -> String,
    packageNameOf: (T) -> String,
    postedAtMsOf: (T) -> Long,
): List<NotificationHistoryLazyRow<T>> {
    if (!groupByApp || searchActive) {
        return items.map { NotificationHistoryLazyRow.Single(it) }
    }
    return buildList {
        val entries = groupNotificationHistoryByApp(
            items = items,
            packageNameOf = packageNameOf,
            postedAtMsOf = postedAtMsOf,
        )
        entries.forEach { entry ->
            when (entry) {
                is NotificationHistoryListEntry.Single -> {
                    add(NotificationHistoryLazyRow.Single(entry.item))
                }
                is NotificationHistoryListEntry.CollapsedGroup -> {
                    val packageName = entry.group.packageName
                    add(NotificationHistoryLazyRow.GroupHeader(entry.group))
                    if (packageName in expandedGroups) {
                        entry.group.items.forEach { item ->
                            add(NotificationHistoryLazyRow.GroupChild(packageName, item))
                        }
                    }
                }
            }
        }
    }
}

private fun <T> NotificationHistoryLazyRow<T>.lazyRowKey(keyOf: (T) -> String): String {
    return when (this) {
        is NotificationHistoryLazyRow.Single -> "single-${keyOf(item)}"
        is NotificationHistoryLazyRow.GroupHeader -> "group-header-${group.packageName}"
        is NotificationHistoryLazyRow.GroupChild -> "group-$packageName-${keyOf(item)}"
    }
}

internal fun <T> LazyListScope.emitGroupedOrFlatNotificationItems(
    items: List<T>,
    groupByApp: Boolean,
    searchActive: Boolean,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    keyOf: (T) -> String,
    packageNameOf: (T) -> String,
    postedAtMsOf: (T) -> Long,
    groupHeaderContent: @Composable (packageName: String, count: Int, latestItem: T, expanded: Boolean) -> Unit,
    rowContent: @Composable (T) -> Unit,
) {
    val lazyRows = buildNotificationHistoryLazyRows(
        items = items,
        groupByApp = groupByApp,
        searchActive = searchActive,
        expandedGroups = expandedGroups,
        keyOf = keyOf,
        packageNameOf = packageNameOf,
        postedAtMsOf = postedAtMsOf,
    )
    items(
        items = lazyRows,
        key = { row -> row.lazyRowKey(keyOf) },
    ) { row ->
        when (row) {
            is NotificationHistoryLazyRow.Single -> {
                Box(Modifier.padding(bottom = 12.dp)) {
                    rowContent(row.item)
                }
            }
            is NotificationHistoryLazyRow.GroupHeader -> {
                val packageName = row.group.packageName
                val expanded = packageName in expandedGroups
                Box(Modifier.padding(bottom = if (expanded) 8.dp else 12.dp)) {
                    groupHeaderContent(
                        packageName,
                        row.group.items.size,
                        row.group.items.first(),
                        expanded,
                    )
                }
            }
            is NotificationHistoryLazyRow.GroupChild -> {
                Box(
                    Modifier
                        .padding(start = 12.dp, bottom = 12.dp),
                ) {
                    rowContent(row.item)
                }
            }
        }
    }
}
