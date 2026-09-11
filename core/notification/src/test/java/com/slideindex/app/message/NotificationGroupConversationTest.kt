package com.slideindex.app.message

import android.app.Notification
import android.graphics.Bitmap
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class NotificationGroupConversationTest {

    @Test
    fun isGroupConversation_readsExplicitFlag() {
        val extras = Bundle().apply {
            putBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, true)
        }

        assertTrue(NotificationData.isGroupConversation(extras))
    }

    @Test
    fun isGroupConversation_falseWhenExplicitFlagIsFalse() {
        val extras = Bundle().apply {
            putBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
            putCharSequence(Notification.EXTRA_CONVERSATION_TITLE, "工作群")
        }

        assertFalse(NotificationData.isGroupConversation(extras))
    }

    @Test
    fun isGroupConversation_fallsBackToConversationTitle() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_CONVERSATION_TITLE, "工作群")
        }

        assertTrue(NotificationData.isGroupConversation(extras))
    }

    @Test
    fun conversationSourceKey_usesGroupNameForGroupConversation() {
        val extras = Bundle().apply {
            putBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, true)
            putCharSequence(Notification.EXTRA_CONVERSATION_TITLE, "工作群")
        }

        assertEquals(
            "group:com.tencent.mm:工作群",
            NotificationData.conversationSourceKey("com.tencent.mm", "张三", extras),
        )
    }

    @Test
    fun conversationSourceKey_usesTitleForDirectMessage() {
        val extras = Bundle.EMPTY

        assertEquals(
            "dm:com.tencent.mm:李四",
            NotificationData.conversationSourceKey("com.tencent.mm", "李四", extras),
        )
    }

    @Test
    fun isGroupConversation_detectsQqNewMessageSuffix() {
        assertTrue(
            NotificationData.isGroupConversation(Bundle.EMPTY, "悬浮菜单交流群(3条新消息)"),
        )
    }

    @Test
    fun conversationSourceKey_stripsQqNewMessageSuffix() {
        assertEquals(
            "group:com.tencent.mobileqq:悬浮菜单交流群",
            NotificationData.conversationSourceKey(
                "com.tencent.mobileqq",
                "悬浮菜单交流群(3条新消息)",
                Bundle.EMPTY,
            ),
        )
    }

    @Test
    fun conversationIdentityKey_ignoresGroupDmPrefixFlip() {
        val groupExtras = Bundle().apply {
            putBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, true)
            putCharSequence(Notification.EXTRA_CONVERSATION_TITLE, "工作群")
        }
        val dmKey = NotificationData.conversationSourceKey(
            "com.tencent.mobileqq",
            "张三",
            Bundle.EMPTY,
        )
        val groupKey = NotificationData.conversationSourceKey(
            "com.tencent.mobileqq",
            "工作群(2条新消息)",
            groupExtras,
        )
        assertEquals(
            NotificationData.conversationIdentityKey(
                "com.tencent.mobileqq",
                "张三",
            ),
            NotificationData.conversationIdentityKey(
                packageName = "com.tencent.mobileqq",
                title = "张三",
            ),
        )
        assertEquals(
            "com.tencent.mobileqq:工作群",
            NotificationData.conversationIdentityKey(
                "com.tencent.mobileqq",
                "工作群(2条新消息)",
            ),
        )
        assertEquals(
            "com.tencent.mobileqq:工作群",
            NotificationData.conversationIdentityKey(
                "com.tencent.mobileqq",
                "某人",
                "工作群",
            ),
        )
        assertNotEquals(dmKey, groupKey)
    }

    @Test
    fun normalizeConversationTitle_stripsQqNewMessageSuffix() {
        assertEquals(
            "悬浮菜单交流群",
            NotificationData.normalizeConversationTitle("悬浮菜单交流群(3条新消息)"),
        )
    }

    @Test
    fun overlayHeaderTitle_prefersConversationTitle() {
        val data = NotificationData(
            packageName = "org.telegram.messenger",
            key = "tg|1",
            title = "一二三睦头人",
            conversationTitle = "YuKongA | Chat",
            content = "hello",
            largeIcon = null,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
        )

        assertEquals("YuKongA | Chat", NotificationData.overlayHeaderTitle(data))
    }

    @Test
    fun overlayHeaderTitle_stripsTelegramGroupSenderSuffix() {
        val data = NotificationData(
            packageName = "org.telegram.messenger",
            key = "tg|2",
            title = "YuKongA | Chat: 一二三睦头人",
            content = "hello",
            largeIcon = null,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
        )

        assertEquals("YuKongA | Chat", NotificationData.overlayHeaderTitle(data))
    }

    @Test
    fun isDistinctMessageSenderIcon_telegramLatestSenderIconNotSessionLevel() {
        val groupIcon = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val senderIcon = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, 0xFFFF0000.toInt())
        }
        val data = NotificationData(
            packageName = "org.telegram.messenger",
            key = "tg|1",
            title = "YuKongA | Chat",
            conversationTitle = "YuKongA | Chat",
            content = "hello",
            largeIcon = senderIcon,
            conversationIcon = groupIcon,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
            conversationSourceKey = "group:org.telegram.messenger:YuKongA | Chat",
        )

        assertFalse(NotificationData.isSessionLevelIcon(senderIcon, data))
        assertTrue(NotificationData.isDistinctMessageSenderIcon(senderIcon, data))
        assertTrue(NotificationData.isSessionLevelIcon(groupIcon, data))
    }

    @Test
    fun isDistinctMessageSenderIcon_groupRejectsSessionLevelIcon() {
        val sessionIcon = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val senderIcon = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, 0xFFFF0000.toInt())
        }
        val data = NotificationData(
            packageName = "com.tencent.mobileqq",
            key = "qq|1",
            title = "项目群",
            conversationTitle = "项目群",
            content = "hello",
            largeIcon = sessionIcon,
            conversationIcon = sessionIcon,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
            conversationSourceKey = "group:com.tencent.mobileqq:项目群",
        )

        assertFalse(NotificationData.isDistinctMessageSenderIcon(sessionIcon, data))
        assertTrue(NotificationData.isDistinctMessageSenderIcon(senderIcon, data))
        assertTrue(NotificationData.isSessionLevelIcon(sessionIcon, data))
    }

    @Test
    fun storedMessageSenderIcon_groupReturnsNull() {
        val icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val group = NotificationData(
            packageName = "com.tencent.mobileqq",
            key = "qq|1",
            title = "项目群",
            conversationTitle = "项目群",
            content = "hello",
            largeIcon = icon,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
            conversationSourceKey = "group:com.tencent.mobileqq:项目群",
        )
        val direct = group.copy(
            conversationTitle = "",
            conversationSourceKey = "dm:com.tencent.mobileqq:Alice",
            title = "Alice",
        )

        assertEquals(null, NotificationData.storedMessageSenderIcon(group))
        assertEquals(icon, NotificationData.storedMessageSenderIcon(direct))
    }
}
