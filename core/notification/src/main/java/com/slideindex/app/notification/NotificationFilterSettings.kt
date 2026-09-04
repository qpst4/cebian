package com.slideindex.app.notification

data class NotificationFilterSettings(
    val notificationHistoryMaxCount: Int = DEFAULT_NOTIFICATION_HISTORY_MAX_COUNT,
    val groupHistoryByApp: Boolean = true,
) {
    companion object {
        const val DEFAULT_NOTIFICATION_HISTORY_MAX_COUNT = 500
        const val MIN_NOTIFICATION_HISTORY_MAX_COUNT = 50
        const val MAX_NOTIFICATION_HISTORY_MAX_COUNT = 2000
    }
}
