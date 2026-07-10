package com.slideindex.app.notification

import android.app.Notification
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.slideindex.app.service.MediaNotificationListener
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class NotificationShadeHiderTest {
    @Test
    fun hideNotification_withoutListener_returnsFalse() {
        val hider = NotificationShadeHider(EmptyNotificationListenerPort)

        assertFalse(hider.hideNotification("missing-key"))
    }

    @Test
    fun unsnoozeNotification_withoutListener_returnsFalse() {
        val hider = NotificationShadeHider(EmptyNotificationListenerPort)

        assertFalse(hider.unsnoozeNotification("missing-key"))
    }

    @Test
    fun restoreAllSnoozed_withoutListener_returnsEmpty() {
        val hider = NotificationShadeHider(EmptyNotificationListenerPort)

        assertTrue(hider.restoreAllSnoozed("com.slideindex.app").isEmpty())
    }

    @Test
    fun hideFromShade_whenNotificationNotActive_returnsTrue() {
        val listener = Robolectric.setupService(MediaNotificationListener::class.java)
        val hider = NotificationShadeHider(FixedNotificationListenerPort(listener))

        assertTrue(hider.hideFromShade(listener, "inactive-key"))
    }

    @Test
    fun hideFromShade_skipsOwnPackageNotifications() {
        val listener = Robolectric.setupService(MediaNotificationListener::class.java)
        val hider = NotificationShadeHider(FixedNotificationListenerPort(listener))
        val sbn = statusBarNotification(listener.packageName)

        assertFalse(hider.hideFromShade(listener, sbn))
    }

    private fun statusBarNotification(packageName: String): StatusBarNotification {
        val context = RuntimeEnvironment.getApplication()
        val notification = Notification.Builder(context, "test-channel")
            .setContentTitle("title")
            .build()
        return StatusBarNotification(
            packageName,
            packageName,
            1,
            "tag",
            Process.myUid(),
            0,
            1,
            notification,
            Process.myUserHandle(),
            System.currentTimeMillis(),
        )
    }

    private object EmptyNotificationListenerPort : NotificationListenerPort {
        override fun listenerOrNull(): NotificationListenerService? = null
    }

    private class FixedNotificationListenerPort(
        private val listener: NotificationListenerService,
    ) : NotificationListenerPort {
        override fun listenerOrNull(): NotificationListenerService? = listener
    }
}
