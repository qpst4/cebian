package com.slideindex.app.message

import android.app.Notification
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
