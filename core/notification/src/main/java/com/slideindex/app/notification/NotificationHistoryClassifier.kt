package com.slideindex.app.notification

data class NotificationHistoryItemMeta(
    val isHidden: Boolean,
    val matchingHideRule: NotificationFilterRule?,
)

data class NotificationHistoryClassification(
    val visibleItems: List<NotificationHistoryItem>,
    val hiddenItems: List<NotificationHistoryItem>,
    val metaById: Map<String, NotificationHistoryItemMeta>,
) {
    fun metaFor(item: NotificationHistoryItem): NotificationHistoryItemMeta {
        return metaById[item.id] ?: NotificationHistoryItemMeta(
            isHidden = item.hidden,
            matchingHideRule = null,
        )
    }

    companion object {
        val Empty = NotificationHistoryClassification(emptyList(), emptyList(), emptyMap())

        fun classify(
            items: List<NotificationHistoryItem>,
            rules: List<NotificationFilterRule>,
        ): NotificationHistoryClassification {
            if (items.isEmpty()) return Empty
            val visible = ArrayList<NotificationHistoryItem>(items.size)
            val hidden = ArrayList<NotificationHistoryItem>()
            val meta = HashMap<String, NotificationHistoryItemMeta>(items.size)
            for (item in items) {
                val matchingRule = findMatchingNotificationFilterRule(rules, item)
                val isHidden = item.hidden || matchingRule != null
                meta[item.id] = NotificationHistoryItemMeta(
                    isHidden = isHidden,
                    matchingHideRule = matchingRule,
                )
                if (isHidden) {
                    hidden.add(item)
                } else {
                    visible.add(item)
                }
            }
            return NotificationHistoryClassification(visible, hidden, meta)
        }
    }
}

fun filterNotificationHistoryBySearch(
    items: List<NotificationHistoryItem>,
    searchQuery: String,
): List<NotificationHistoryItem> {
    val query = searchQuery.trim().lowercase()
    if (query.isEmpty()) return items
    return items.filter { item ->
        item.title.lowercase().contains(query) ||
            item.text.lowercase().contains(query) ||
            item.packageName.lowercase().contains(query)
    }
}

data class NotificationHistoryUiState(
    val classification: NotificationHistoryClassification = NotificationHistoryClassification.Empty,
    val filteredHistoryItems: List<NotificationHistoryItem> = emptyList(),
    val filteredHiddenItems: List<NotificationHistoryItem> = emptyList(),
    val activeNotifications: List<ActiveNotificationEntry> = emptyList(),
    val activeKeys: Set<String> = emptySet(),
)

fun computeNotificationHistoryUiState(
    items: List<NotificationHistoryItem>,
    rules: List<NotificationFilterRule>,
    listenerEnabled: Boolean,
    searchQuery: String,
    activeNotificationsProvider: () -> List<ActiveNotificationEntry>,
    activeKeysProvider: () -> Set<String>,
): NotificationHistoryUiState {
    val classification = NotificationHistoryClassification.classify(items, rules)
    val filteredHistoryItems = filterNotificationHistoryBySearch(classification.visibleItems, searchQuery)
    val filteredHiddenItems = filterNotificationHistoryBySearch(classification.hiddenItems, searchQuery)
    if (!listenerEnabled) {
        return NotificationHistoryUiState(
            classification = classification,
            filteredHistoryItems = filteredHistoryItems,
            filteredHiddenItems = filteredHiddenItems,
        )
    }
    val activeNotifications = activeNotificationsProvider()
        .filter { entry ->
            val item = entry.historyItem ?: entry.toActiveHistoryItem()
            !classification.metaFor(item).isHidden
        }
    return NotificationHistoryUiState(
        classification = classification,
        filteredHistoryItems = filteredHistoryItems,
        filteredHiddenItems = filteredHiddenItems,
        activeNotifications = activeNotifications,
        activeKeys = activeKeysProvider(),
    )
}

private fun ActiveNotificationEntry.toActiveHistoryItem(): NotificationHistoryItem {
    return NotificationHistoryItem(
        packageName = packageName,
        title = title,
        text = text,
        postedAtMs = postedAtMs,
        intentUri = null,
        notificationKey = key,
    )
}
