package com.slideindex.app.message

import android.app.Notification
import android.graphics.Bitmap
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class NotificationMessagingHistoryTest {

    @Test
    fun extractMessages_readsMessagingStyleEntries() {
        val context = RuntimeEnvironment.getApplication()
        val first = Bundle().apply {
            putCharSequence("text", "第一条")
            putLong("time", 1_000L)
            putCharSequence("sender", "Alice")
        }
        val second = Bundle().apply {
            putCharSequence("text", "第二条")
            putLong("time", 2_000L)
            putCharSequence("sender", "Bob")
        }
        val extras = Bundle().apply {
            putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf(first, second))
        }

        val messages = NotificationMessagingHistory.extractMessages(context, extras)

        assertEquals(2, messages.size)
        assertEquals("第一条", messages[0].text)
        assertEquals("第二条", messages[1].text)
    }

    @Test
    fun extractMessages_readsTextLinesFallback() {
        val context = RuntimeEnvironment.getApplication()
        val extras = Bundle().apply {
            putCharSequenceArray(
                "android.textLines",
                arrayOf("张三: 你好", "李四: 在吗"),
            )
        }

        val messages = NotificationMessagingHistory.extractMessages(context, extras)

        assertEquals(2, messages.size)
        assertEquals("张三: 你好", messages[0].text)
    }

    @Test
    fun resolveUnreadCount_prefersMessageAndLineCounts() {
        val messages = listOf(
            NotificationMessage("a"),
            NotificationMessage("b"),
        )
        assertEquals(2, NotificationMessagingHistory.resolveUnreadCount(messages, 0, 0, 1))
        assertEquals(3, NotificationMessagingHistory.resolveUnreadCount(emptyList(), 0, 3, 1))
        assertEquals(5, NotificationMessagingHistory.resolveUnreadCount(emptyList(), 5, 0, 1))
    }

    @Test
    fun mergeHistory_appendsOnlyNewMessages() {
        val existing = listOf(NotificationMessage(text = "A", timestamp = 1L))
        val incoming = listOf(
            NotificationMessage(text = "A", timestamp = 1L),
            NotificationMessage(text = "B", timestamp = 2L),
        )

        val merged = NotificationMessagingHistory.mergeHistory(existing, incoming)

        assertEquals(2, merged.size)
        assertEquals("B", merged[0].text)
    }

    @Test
    fun mergeHistory_appendsLatestContentWhenIncomingEmpty() {
        val existing = listOf(NotificationMessage(text = "第一条", timestamp = 1L))

        val merged = NotificationMessagingHistory.mergeHistory(
            existing = existing,
            incoming = emptyList(),
            latestContent = "第二条",
            latestTimestamp = 2L,
        )

        assertEquals(2, merged.size)
        assertEquals("第二条", merged[0].text)
    }

    @Test
    fun mergeHistory_inheritsSenderWhenLatestContentHasNoMetadata() {
        val icon = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val existing = listOf(
            NotificationMessage(
                text = "第一条",
                timestamp = 1L,
                sender = "吃五块大鸡排",
                senderIcon = icon,
            ),
        )

        val merged = NotificationMessagingHistory.mergeHistory(
            existing = existing,
            incoming = emptyList(),
            latestContent = "第二条",
            latestTimestamp = 2L,
        )

        assertEquals("吃五块大鸡排", merged[0].sender)
        assertEquals(null, merged[0].senderIcon)
    }

    @Test
    fun inheritConsecutiveMessagingSenders_fillsMissingSenderOnly() {
        val icon = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val inherited = NotificationMessagingHistory.inheritConsecutiveMessagingSenders(
            listOf(
                NotificationMessage(
                    text = "第一条",
                    sender = "吃五块大鸡排",
                    senderIcon = icon,
                ),
                NotificationMessage(text = "第二条"),
            ),
        )

        assertEquals("吃五块大鸡排", inherited[1].sender)
        assertEquals(null, inherited[1].senderIcon)
    }

    @Test
    fun extractMessages_inheritsSenderForConsecutiveMessagingEntries() {
        val context = RuntimeEnvironment.getApplication()
        val first = Bundle().apply {
            putCharSequence("text", "第一条")
            putLong("time", 1_000L)
            putCharSequence("sender", "吃五块大鸡排")
        }
        val second = Bundle().apply {
            putCharSequence("text", "第二条")
            putLong("time", 2_000L)
        }
        val extras = Bundle().apply {
            putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf(first, second))
        }

        val messages = NotificationMessagingHistory.extractMessages(context, extras)

        assertEquals("吃五块大鸡排", messages[1].sender)
    }

    @Test
    fun mergeHistory_preservesHistoryWhenSnapshotIsShorter() {
        val existing = listOf(
            NotificationMessage(text = "C", timestamp = 3L),
            NotificationMessage(text = "B", timestamp = 2L),
            NotificationMessage(text = "A", timestamp = 1L),
        )
        val incoming = listOf(
            NotificationMessage(text = "A", timestamp = 1L),
            NotificationMessage(text = "B", timestamp = 2L),
        )

        val merged = NotificationMessagingHistory.mergeHistory(existing, incoming)

        assertEquals(3, merged.size)
        assertEquals("C", merged[0].text)
    }

    @Test
    fun mergeHistory_replacesWithMessagingStyleSnapshot() {
        val existing = listOf(
            NotificationMessage(text = "A", timestamp = 1L),
            NotificationMessage(text = "B", timestamp = 2L),
        )
        val incoming = listOf(
            NotificationMessage(text = "A", timestamp = 1L),
            NotificationMessage(text = "B", timestamp = 2L),
            NotificationMessage(text = "C", timestamp = 3L),
        )

        val merged = NotificationMessagingHistory.mergeHistory(existing, incoming)

        assertEquals(3, merged.size)
        assertEquals("C", merged[0].text)
    }

    @Test
    fun parseTitleUnreadHint_readsChineseCount() {
        assertEquals(5, NotificationMessagingHistory.parseTitleUnreadHint("工作群(5条新消息)"))
    }

    @Test
    fun messagesNewestFirst_reversesChronologicalOrder() {
        val ordered = NotificationMessagingHistory.messagesNewestFirst(
            listOf(
                NotificationMessage(text = "旧", timestamp = 1L),
                NotificationMessage(text = "中", timestamp = 2L),
                NotificationMessage(text = "新", timestamp = 3L),
            ),
        )

        assertEquals(listOf("新", "中", "旧"), ordered.map { it.text })
    }

    @Test
    fun messagesNewestFirst_reversesWhenTimestampMissing() {
        val ordered = NotificationMessagingHistory.messagesNewestFirst(
            listOf(
                NotificationMessage(text = "第一条"),
                NotificationMessage(text = "第二条"),
            ),
        )

        assertEquals(listOf("第二条", "第一条"), ordered.map { it.text })
    }

    @Test
    fun messagesOldestFirst_reversesNewestFirstStorage() {
        val ordered = NotificationMessagingHistory.messagesOldestFirst(
            listOf(
                NotificationMessage(text = "新", timestamp = 3L),
                NotificationMessage(text = "旧", timestamp = 1L),
            ),
        )

        assertEquals(listOf("旧", "新"), ordered.map { it.text })
    }

    @Test
    fun mergeHistory_doesNotDuplicateWhenTimestampDiffers() {
        val existing = listOf(
            NotificationMessage(text = "重复", timestamp = 1L, sender = "六月"),
        )
        val incoming = listOf(
            NotificationMessage(text = "重复", timestamp = 2L, sender = "六月"),
        )

        val merged = NotificationMessagingHistory.mergeHistory(existing, incoming)

        assertEquals(1, merged.size)
        assertEquals("重复", merged.first().text)
    }

    @Test
    fun latestConversationActivityTime_prefersMessageTimestampOverPostTime() {
        val messages = listOf(
            NotificationMessage(text = "旧", timestamp = 1_000L),
            NotificationMessage(text = "新", timestamp = 2_000L),
        )

        assertEquals(
            2_000L,
            NotificationMessagingHistory.latestConversationActivityTime(messages, postTime = 9_999L),
        )
    }

    @Test
    fun mergeHistory_doesNotDuplicateLatestContentAlreadyPresent() {
        val existing = listOf(
            NotificationMessage(text = "已有", timestamp = 1L),
        )

        val merged = NotificationMessagingHistory.mergeHistory(
            existing = existing,
            incoming = emptyList(),
            latestContent = "已有",
            latestTimestamp = 9_999L,
        )

        assertEquals(1, merged.size)
        assertEquals("已有", merged.first().text)
    }

    @Test
    fun peekContentSignature_ignoresNotificationMetadataRefresh() {
        val latest = NotificationMessage(text = "hello", timestamp = 100L, sender = "Alice")
        val data = NotificationData(
            packageName = "org.telegram.messenger",
            key = "tg|1",
            title = "Alice",
            content = "hello",
            largeIcon = null,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
            badgeCount = 1,
        )
        val refreshed = data.copy(postTime = 9_999L, badgeCount = 5, title = "Alice (5)")

        val original = NotificationMessagingHistory.peekContentSignature(latest, data)
        val afterRefresh = NotificationMessagingHistory.peekContentSignature(latest, refreshed)

        assertEquals(original, afterRefresh)
    }

    @Test
    fun peekContentSignature_changesWhenMessageBodyChanges() {
        val data = NotificationData(
            packageName = "org.telegram.messenger",
            key = "tg|1",
            title = "Alice",
            content = "world",
            largeIcon = null,
            appIcon = null,
            contentIntent = null,
            postTime = 1L,
        )
        val before = NotificationMessagingHistory.peekContentSignature(
            NotificationMessage(text = "hello", timestamp = 100L),
            data,
        )
        val after = NotificationMessagingHistory.peekContentSignature(
            NotificationMessage(text = "world", timestamp = 200L),
            data,
        )

        assertEquals("hello|100|", before)
        assertEquals("world|200|", after)
    }
}
