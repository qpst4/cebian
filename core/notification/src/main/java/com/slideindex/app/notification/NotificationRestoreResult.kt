package com.slideindex.app.notification

enum class NotificationRestoreResult {
    /** Notification was unsnoozed and should reappear in the shade. */
    RESTORED_TO_SHADE,

    /** Hide rule removed; future matching notifications will show normally. */
    RULE_REMOVED_ONLY,

    /** Could not unsnooze and no matching hide rule was found. */
    UNSNOOZE_FAILED,
}
