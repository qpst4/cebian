package com.slideindex.app.notification

data class NotificationHistoryAppGroup<T>(
    val packageName: String,
    val items: List<T>,
    val latestPostedAtMs: Long,
) {
    val isCollapsible: Boolean get() = items.size >= 2
}

sealed interface NotificationHistoryListEntry<out T> {
    data class Single<T>(val item: T) : NotificationHistoryListEntry<T>

    data class CollapsedGroup<T>(
        val group: NotificationHistoryAppGroup<T>,
    ) : NotificationHistoryListEntry<T>
}

fun <T> groupNotificationHistoryByApp(
    items: List<T>,
    packageNameOf: (T) -> String,
    postedAtMsOf: (T) -> Long,
    minCountToCollapse: Int = 2,
): List<NotificationHistoryListEntry<T>> {
    if (items.isEmpty()) return emptyList()
    val grouped = items.groupBy(packageNameOf)
    val entries = grouped.flatMap { (packageName, groupItems) ->
        val sorted = groupItems.sortedByDescending(postedAtMsOf)
        if (sorted.size < minCountToCollapse) {
            sorted.map { NotificationHistoryListEntry.Single(it) }
        } else {
            listOf(
                NotificationHistoryListEntry.CollapsedGroup(
                    NotificationHistoryAppGroup(
                        packageName = packageName,
                        items = sorted,
                        latestPostedAtMs = postedAtMsOf(sorted.first()),
                    ),
                ),
            )
        }
    }
    return entries.sortedByDescending { entry ->
        when (entry) {
            is NotificationHistoryListEntry.Single -> postedAtMsOf(entry.item)
            is NotificationHistoryListEntry.CollapsedGroup -> entry.group.latestPostedAtMs
        }
    }
}
