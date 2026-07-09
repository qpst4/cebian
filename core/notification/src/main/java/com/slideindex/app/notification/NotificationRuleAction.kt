package com.slideindex.app.notification

enum class NotificationRuleAction {
    /** Remove the notification from the shade (snooze). */
    HIDE,
    /** Read title and text aloud via TTS. */
    TTS,
    /** Play the default notification ringtone. */
    RING,
    /** Open the notification content intent. */
    OPEN,
}
