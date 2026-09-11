package com.slideindex.app.overlay

import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.NotificationData
import com.slideindex.app.message.NotificationMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CNoticeListUiStateTest {
    private val settings = MessageSettings()

    @Test
    fun outsideTouch_fromActiveStates() {
        assertEquals(
            CNoticeListPhase.ExpandedDimmed,
            CNoticeListUiState.onOutsideTouch(CNoticeListPhase.ExpandedActive),
        )
        assertEquals(
            CNoticeListPhase.CollapsedDimmed,
            CNoticeListUiState.onOutsideTouch(CNoticeListPhase.Collapsed),
        )
        assertEquals(
            CNoticeListPhase.ExpandedDimmed,
            CNoticeListUiState.onOutsideTouch(CNoticeListPhase.ExpandedDimmed),
        )
    }

    @Test
    fun ghostTimeout_fromDimmedStates() {
        assertEquals(
            CNoticeListPhase.ExpandedGhost,
            CNoticeListUiState.onGhostTimeout(CNoticeListPhase.ExpandedDimmed),
        )
        assertEquals(
            CNoticeListPhase.CollapsedGhost,
            CNoticeListUiState.onGhostTimeout(CNoticeListPhase.CollapsedDimmed),
        )
        assertEquals(
            CNoticeListPhase.ExpandedActive,
            CNoticeListUiState.onGhostTimeout(CNoticeListPhase.ExpandedActive),
        )
    }

    @Test
    fun handleVisibility_matchesPhase() {
        assertTrue(CNoticeListUiState.handleVisible(CNoticeListPhase.ExpandedActive))
        assertTrue(CNoticeListUiState.handleVisible(CNoticeListPhase.Collapsed))
        assertFalse(CNoticeListUiState.handleVisible(CNoticeListPhase.ExpandedGhost))
        assertFalse(CNoticeListUiState.handleVisible(CNoticeListPhase.CollapsedGhost))
    }

    @Test
    fun shouldRemoveEntryAfterReconcile_keepsActiveConversationWhenTokensCleared() {
        assertFalse(
            CNoticeListUiState.shouldRemoveEntryAfterReconcile(
                conversationSourceKey = "com.tencent.mobileqq:Alice",
                removedConversationKey = "com.tencent.mobileqq:Alice",
                activeConversationKeys = setOf("com.tencent.mobileqq:Alice"),
                displayedPostKeysEmpty = true,
            ),
        )
    }

    @Test
    fun shouldRemoveEntryAfterReconcile_neverRemovesPersistentEntry() {
        assertFalse(
            CNoticeListUiState.shouldRemoveEntryAfterReconcile(
                conversationSourceKey = "com.tencent.mobileqq:Alice",
                removedConversationKey = "com.tencent.mobileqq:Alice",
                activeConversationKeys = emptySet(),
                displayedPostKeysEmpty = true,
            ),
        )
    }

    @Test
    fun listAlpha_usesConfiguredOpacities() {
        val custom = settings.copy(
            cNoticeOpacity = 1f,
            cNoticeDimmedOpacity = 0.4f,
            cNoticeGhostOpacity = 0.2f,
        )
        assertEquals(1f, CNoticeListUiState.listAlpha(CNoticeListPhase.ExpandedActive, custom))
        assertEquals(0.4f, CNoticeListUiState.listAlpha(CNoticeListPhase.ExpandedDimmed, custom))
        assertEquals(0.2f, CNoticeListUiState.listAlpha(CNoticeListPhase.ExpandedGhost, custom))
        assertEquals(0.4f, CNoticeListUiState.listAlpha(CNoticeListPhase.CollapsedDimmed, custom))
        assertEquals(0.2f, CNoticeListUiState.listAlpha(CNoticeListPhase.CollapsedGhost, custom))
    }

    @Test
    fun listAlpha_collapsedActiveUsesBaseOpacity() {
        val custom = settings.copy(
            cNoticeOpacity = 0.9f,
            cNoticeDimmedOpacity = 0.4f,
            cNoticeGhostOpacity = 0.2f,
        )
        assertEquals(0.9f, CNoticeListUiState.listAlpha(CNoticeListPhase.Collapsed, custom))
    }

    @Test
    fun formatPeekBannerText_groupMessagingStyle_includesSender() {
        val data = NotificationData(
            packageName = "org.telegram.messenger",
            key = "tg|1",
            title = "项目群",
            conversationTitle = "项目群",
            content = "hello",
            largeIcon = null,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
            conversationSourceKey = "group:org.telegram.messenger:项目群",
        )
        val message = NotificationMessage(text = "hello", sender = "Alice")

        assertEquals(
            "Alice: hello",
            CNoticeListUiState.formatPeekBannerText(data, message),
        )
    }

    @Test
    fun formatPeekBannerText_directChat_stripsRedundantSender() {
        val data = NotificationData(
            packageName = "org.telegram.messenger",
            key = "tg|2",
            title = "Alice",
            content = "Alice: hello",
            largeIcon = null,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
            conversationSourceKey = "dm:org.telegram.messenger:Alice",
        )

        assertEquals(
            "hello",
            CNoticeListUiState.formatPeekBannerText(
                data,
                NotificationMessage(text = "Alice: hello", sender = "Alice"),
            ),
        )
    }

    @Test
    fun formatMessageRowBody_groupStripsRedundantSenderPrefix() {
        val data = NotificationData(
            packageName = "com.tencent.mobileqq",
            key = "qq|4",
            title = "枯溪的Flyme交流群",
            conversationTitle = "枯溪的Flyme交流群",
            content = "hello",
            largeIcon = null,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
            conversationSourceKey = "group:com.tencent.mobileqq:枯溪的Flyme交流群",
        )
        val message = NotificationMessage(
            text = "18_: 但是什么时候赔就不知道了",
            sender = "18_",
        )

        assertEquals(
            "但是什么时候赔就不知道了",
            CNoticeListUiState.formatMessageRowBody(message, data),
        )
    }

    @Test
    fun formatMessageRowBody_groupKeepsBodyWhenNoSenderLine() {
        val data = NotificationData(
            packageName = "com.tencent.mobileqq",
            key = "qq|5",
            title = "项目群",
            conversationTitle = "项目群",
            content = "hello",
            largeIcon = null,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
            conversationSourceKey = "group:com.tencent.mobileqq:项目群",
        )
        val message = NotificationMessage(text = "18_: 你好")

        assertEquals("18_: 你好", CNoticeListUiState.formatMessageRowBody(message, data))
    }

    @Test
    fun resolveMessageRowAvatar_groupWithoutSenderIcon_usesLabelNotLargeIcon() {
        val data = NotificationData(
            packageName = "com.tencent.mobileqq",
            key = "qq|1",
            title = "项目群",
            conversationTitle = "项目群",
            content = "hello",
            largeIcon = null,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
            conversationSourceKey = "group:com.tencent.mobileqq:项目群",
        )
        val message = NotificationMessage(text = "你好", sender = "张三")

        assertEquals(null, CNoticeListUiState.resolveMessageRowAvatarBitmap(message, data))
        assertEquals("张三", CNoticeListUiState.resolveMessageRowAvatarLabel(message, data))
        assertTrue(CNoticeListUiState.shouldShowMessageRowAvatar(message, data))
    }

    @Test
    fun resolveMessageRowAvatar_groupParsesSenderFromTextPrefix() {
        val data = NotificationData(
            packageName = "com.tencent.mobileqq",
            key = "qq|2",
            title = "项目群",
            conversationTitle = "项目群",
            content = "李四: 在吗",
            largeIcon = null,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
            conversationSourceKey = "group:com.tencent.mobileqq:项目群",
        )
        val message = NotificationMessage(text = "李四: 在吗")

        assertEquals("李四", CNoticeListUiState.resolveMessageRowAvatarLabel(message, data))
    }

    @Test
    fun resolveMessageRowAvatar_directChat_withoutIcon_usesHeaderTitleLabel() {
        val data = NotificationData(
            packageName = "com.tencent.mobileqq",
            key = "qq|3",
            title = "Alice",
            content = "hi",
            largeIcon = null,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
            conversationSourceKey = "dm:com.tencent.mobileqq:Alice",
        )
        val message = NotificationMessage(text = "hi")

        assertEquals(null, CNoticeListUiState.resolveMessageRowAvatarBitmap(message, data))
        assertEquals("Alice", CNoticeListUiState.resolveMessageRowAvatarLabel(message, data))
    }

    @Test
    fun enrichMessageRowsForDisplay_usesLargeIconForLatestSenderInGroup() {
        val groupIcon = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        val senderIcon = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, 0xFFFF0000.toInt())
        }
        val data = NotificationData(
            packageName = "org.telegram.messenger",
            key = "tg|2",
            title = "YuKongA | Chat",
            conversationTitle = "YuKongA | Chat",
            content = "图片",
            largeIcon = senderIcon,
            conversationIcon = groupIcon,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
            conversationSourceKey = "group:org.telegram.messenger:YuKongA | Chat",
            messages = listOf(
                NotificationMessage(text = "图片", sender = "caged bird"),
            ),
        )
        val enriched = CNoticeListUiState.enrichMessageRowsForDisplay(
            listOf(
                NotificationMessage(text = "萨莉亚现在的披萨不好吃，吃着发苦", sender = "caged bird"),
                NotificationMessage(text = "图片", sender = "caged bird"),
            ),
            data,
        )

        assertEquals(senderIcon, enriched[0].senderIcon)
        assertEquals(senderIcon, enriched[1].senderIcon)
        assertEquals(senderIcon, CNoticeListUiState.resolveMessageRowAvatarBitmap(enriched[1], data))
    }

    @Test
    fun enrichMessageRowsForDisplay_inheritsSenderFromPreviousRow() {
        val data = NotificationData(
            packageName = "com.tencent.mobileqq",
            key = "qq|1",
            title = "项目群",
            conversationTitle = "项目群",
            content = "hello",
            largeIcon = null,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
            conversationSourceKey = "group:com.tencent.mobileqq:项目群",
        )
        val enriched = CNoticeListUiState.enrichMessageRowsForDisplay(
            listOf(
                NotificationMessage(text = "第一条", sender = "吃五块大鸡排"),
                NotificationMessage(text = "第二条"),
            ),
            data,
        )

        assertEquals("吃五块大鸡排", enriched[1].sender)
        assertEquals(
            "吃五块大鸡排",
            CNoticeListUiState.resolveMessageRowAvatarLabel(enriched[1], data),
        )
    }

    @Test
    fun toggleCollapse_switchesFamilies() {
        assertEquals(
            CNoticeListPhase.ExpandedActive,
            CNoticeListUiState.toggleCollapse(CNoticeListPhase.Collapsed),
        )
        assertEquals(
            CNoticeListPhase.ExpandedActive,
            CNoticeListUiState.toggleCollapse(CNoticeListPhase.CollapsedDimmed),
        )
        assertEquals(
            CNoticeListPhase.Collapsed,
            CNoticeListUiState.toggleCollapse(CNoticeListPhase.ExpandedActive),
        )
    }
}
