package com.slideindex.app.notification

fun isNotificationContentChanged(
    existing: NotificationHistoryItem,
    incoming: NotificationHistoryItem,
): Boolean {
    return existing.title != incoming.title || existing.text != incoming.text
}

fun resolveNotificationHistoryRecord(
    current: List<NotificationHistoryItem>,
    incoming: NotificationHistoryItem,
    mergeCapture: (NotificationHistoryItem?, NotificationHistoryItem) -> NotificationHistoryItem,
): List<NotificationHistoryItem> {
    val key = incoming.notificationKey
    if (key.isNullOrBlank()) {
        return listOf(incoming) + current
    }
    val existing = current.firstOrNull { it.notificationKey == key }
    if (existing == null) {
        return listOf(incoming) + current
    }
    if (!isNotificationContentChanged(existing, incoming)) {
        val merged = mergeCapture(existing, incoming).copy(id = existing.id)
        val withoutExisting = current.filterNot { it.id == existing.id }
        return listOf(merged) + withoutExisting
    }
    return listOf(incoming) + current
}
