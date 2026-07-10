package com.slideindex.app.message

import android.app.Notification
import android.content.Context
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MessageNotificationFilterTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    @After
    fun resetState() {
        MessageNotificationFilter.resetForTesting()
    }

    @Test
    fun dedup_rejectsDuplicatePostKey() {
        val data = sampleData(key = "k1", title = "A", content = "1")
        assertTrue(MessageNotificationFilter.dedup(data))
        assertFalse(MessageNotificationFilter.dedup(data))
    }

    @Test
    fun dedup_rejectsDuplicateContentWithSameKey() {
        val first = sampleData(key = "k1", title = "Hello", content = "World", postTime = 1L)
        val second = sampleData(key = "k1", title = "Hello", content = "World", postTime = 2L)
        assertTrue(MessageNotificationFilter.dedup(first))
        assertFalse(MessageNotificationFilter.dedup(second))
    }

    @Test
    fun shouldShowNotification_blocksOwnPackage() {
        val sbn = statusBarNotification(context.packageName, "self")
        val data = sampleData(packageName = context.packageName)
        assertFalse(
            MessageNotificationFilter.shouldShowNotification(
                context = context,
                settings = MessageSettings(),
                sbn = sbn,
                data = data,
                environmentPort = object : MessageEnvironmentPort {
                    override fun isSystemDndEnabled(context: Context) = false
                },
                foregroundPort = object : MessageForegroundPort {
                    override fun foregroundPackage() = null
                },
            ),
        )
    }

    @Test
    fun shouldShowNotification_blocksDisabledPackage() {
        val sbn = statusBarNotification("com.example.chat", "chat")
        val data = sampleData(packageName = "com.example.chat")
        val settings = MessageSettings(disabledPackages = setOf("com.example.chat"))
        assertFalse(
            MessageNotificationFilter.shouldShowNotification(
                context = context,
                settings = settings,
                sbn = sbn,
                data = data,
                environmentPort = object : MessageEnvironmentPort {
                    override fun isSystemDndEnabled(context: Context) = false
                },
                foregroundPort = object : MessageForegroundPort {
                    override fun foregroundPackage() = null
                },
            ),
        )
    }

    @Test
    fun shouldShowNotification_blocksSystemDndWhenConfigured() {
        val sbn = statusBarNotification("com.example.chat", "chat")
        val data = sampleData(packageName = "com.example.chat")
        val settings = MessageSettings(suppressWhenSystemDnd = true)
        assertFalse(
            MessageNotificationFilter.shouldShowNotification(
                context = context,
                settings = settings,
                sbn = sbn,
                data = data,
                environmentPort = object : MessageEnvironmentPort {
                    override fun isSystemDndEnabled(context: Context) = true
                },
                foregroundPort = object : MessageForegroundPort {
                    override fun foregroundPackage() = null
                },
            ),
        )
    }

    @Test
    fun shouldShowNotification_allowsNormalNotification() {
        val sbn = statusBarNotification("com.example.chat", "chat")
        val data = sampleData(packageName = "com.example.chat")
        assertTrue(
            MessageNotificationFilter.shouldShowNotification(
                context = context,
                settings = MessageSettings(),
                sbn = sbn,
                data = data,
                environmentPort = object : MessageEnvironmentPort {
                    override fun isSystemDndEnabled(context: Context) = false
                },
                foregroundPort = object : MessageForegroundPort {
                    override fun foregroundPackage() = null
                },
            ),
        )
    }

    private fun sampleData(
        packageName: String = "com.example.chat",
        key: String = "key",
        title: String = "Title",
        content: String = "Body",
        postTime: Long = 1_700_000_000_000L,
    ) = NotificationData(
        packageName = packageName,
        key = key,
        title = title,
        content = content,
        largeIcon = null,
        appIcon = null,
        contentIntent = null,
        postTime = postTime,
    )

    private fun statusBarNotification(packageName: String, tag: String): StatusBarNotification {
        val notification = Notification.Builder(context, "test")
            .setContentTitle("title")
            .setContentText("text")
            .build()
        return StatusBarNotification(
            packageName,
            packageName,
            1,
            tag,
            1000,
            1,
            0,
            notification,
            UserHandle.getUserHandleForUid(0),
            System.currentTimeMillis(),
        )
    }
}
