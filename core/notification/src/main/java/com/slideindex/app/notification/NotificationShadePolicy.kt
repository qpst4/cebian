package com.slideindex.app.notification

import android.app.Notification
import android.service.notification.StatusBarNotification

object NotificationShadePolicy {
    /** True for ongoing / non-dismissible shade notifications (e.g. overlay, FGS). */
    fun isOngoing(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return true
        if ((notification.flags and Notification.FLAG_NO_CLEAR) != 0) return true
        return !sbn.isClearable
    }
}
