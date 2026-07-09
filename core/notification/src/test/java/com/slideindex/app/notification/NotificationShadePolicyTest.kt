package com.slideindex.app.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class NotificationShadePolicyTest {
    @Test
    fun isOngoing_trueForOngoingFlag() {
        val context = RuntimeEnvironment.getApplication()
        val notification = Notification.Builder(context, "ch")
            .setContentTitle("t")
            .setFlag(Notification.FLAG_ONGOING_EVENT, true)
            .build()
        val sbn = statusBarNotification(notification)
        assertTrue(NotificationShadePolicy.isOngoing(sbn))
    }

    @Test
    fun isOngoing_falseForClearableNotification() {
        val context = RuntimeEnvironment.getApplication()
        val notification = Notification.Builder(context, "ch")
            .setContentTitle("t")
            .build()
        val sbn = statusBarNotification(notification)
        assertFalse(NotificationShadePolicy.isOngoing(sbn))
    }

    private fun statusBarNotification(notification: Notification): StatusBarNotification =
        StatusBarNotification(
            "pkg",
            "pkg",
            1,
            "tag",
            1000,
            1000,
            0,
            notification,
            android.os.Process.myUserHandle(),
            0L,
        )
}
