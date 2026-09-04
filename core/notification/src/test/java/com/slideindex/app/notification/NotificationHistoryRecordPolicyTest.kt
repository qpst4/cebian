package com.slideindex.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHistoryRecordPolicyTest {

    private fun item(
        id: String,
        key: String? = "key-1",
        title: String = "Group",
        text: String = "Message",
        postedAtMs: Long = 100L,
    ) = NotificationHistoryItem(
        id = id,
        packageName = "org.telegram.messenger",
        title = title,
        text = text,
        postedAtMs = postedAtMs,
        intentUri = null,
        notificationKey = key,
    )

    private fun mergeCapture(
        existing: NotificationHistoryItem?,
        incoming: NotificationHistoryItem,
    ): NotificationHistoryItem {
        if (existing == null) return incoming
        return incoming.copy(intentUri = incoming.intentUri ?: existing.intentUri)
    }

    @Test
    fun sameKeySameContent_replacesInPlaceKeepingId() {
        val existing = item(id = "hist-1", text = "Hello")
        val incoming = item(id = "hist-2", text = "Hello", postedAtMs = 200L)
        val next = resolveNotificationHistoryRecord(listOf(existing), incoming, ::mergeCapture)
        assertEquals(1, next.size)
        assertEquals("hist-1", next.first().id)
        assertEquals(200L, next.first().postedAtMs)
    }

    @Test
    fun sameKeyDifferentText_appendsNewEntry() {
        val existing = item(id = "hist-1", text = "First")
        val incoming = item(id = "hist-2", text = "Second", postedAtMs = 200L)
        val next = resolveNotificationHistoryRecord(listOf(existing), incoming, ::mergeCapture)
        assertEquals(2, next.size)
        assertEquals("hist-2", next[0].id)
        assertEquals("Second", next[0].text)
        assertEquals("hist-1", next[1].id)
        assertEquals("First", next[1].text)
    }

    @Test
    fun sameKeyDifferentTitle_appendsNewEntry() {
        val existing = item(id = "hist-1", title = "Alice", text = "Hi")
        val incoming = item(id = "hist-2", title = "Bob", text = "Hi", postedAtMs = 200L)
        val next = resolveNotificationHistoryRecord(listOf(existing), incoming, ::mergeCapture)
        assertEquals(2, next.size)
        assertEquals("Bob", next[0].title)
        assertEquals("Alice", next[1].title)
    }

    @Test
    fun blankKey_alwaysPrependsWithoutDedup() {
        val existing = item(id = "hist-1", key = null, text = "First")
        val incoming = item(id = "hist-2", key = null, text = "Second")
        val next = resolveNotificationHistoryRecord(listOf(existing), incoming, ::mergeCapture)
        assertEquals(2, next.size)
        assertEquals("hist-2", next[0].id)
    }

    @Test
    fun telegramGroupMessages_keepHistoryPerMessage() {
        val messages = listOf("msg-1", "msg-2", "msg-3")
        var history = emptyList<NotificationHistoryItem>()
        messages.forEachIndexed { index, text ->
            val incoming = item(
                id = "id-$index",
                text = text,
                postedAtMs = 100L + index,
            )
            history = resolveNotificationHistoryRecord(history, incoming, ::mergeCapture)
        }
        assertEquals(3, history.size)
        assertEquals(listOf("msg-3", "msg-2", "msg-1"), history.map { it.text })
    }

    @Test
    fun contentChangedDetection_ignoresPostedAtOnly() {
        val existing = item(id = "hist-1", text = "Same")
        val incoming = item(id = "hist-2", text = "Same", postedAtMs = 999L)
        assertTrue(!isNotificationContentChanged(existing, incoming))
    }
}
