package com.slideindex.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHistoryGroupingTest {

    private data class Sample(
        val id: String,
        val packageName: String,
        val postedAtMs: Long,
    )

    private fun group(items: List<Sample>) = groupNotificationHistoryByApp(
        items = items,
        packageNameOf = { it.packageName },
        postedAtMsOf = { it.postedAtMs },
    )

    @Test
    fun empty_returnsEmpty() {
        assertTrue(group(emptyList()).isEmpty())
    }

    @Test
    fun singleItemPerApp_emitsSinglesInTimeOrder() {
        val items = listOf(
            Sample("a", "com.a", 300L),
            Sample("b", "com.b", 200L),
            Sample("c", "com.c", 100L),
        )
        val entries = group(items)
        assertEquals(3, entries.size)
        assertTrue(entries[0] is NotificationHistoryListEntry.Single)
        assertEquals("a", (entries[0] as NotificationHistoryListEntry.Single).item.id)
        assertEquals("b", (entries[1] as NotificationHistoryListEntry.Single).item.id)
        assertEquals("c", (entries[2] as NotificationHistoryListEntry.Single).item.id)
    }

    @Test
    fun sameAppTwoItems_emitsCollapsedGroupSortedByTime() {
        val items = listOf(
            Sample("old", "com.chat", 100L),
            Sample("new", "com.chat", 200L),
        )
        val entries = group(items)
        assertEquals(1, entries.size)
        val group = (entries.single() as NotificationHistoryListEntry.CollapsedGroup).group
        assertEquals("com.chat", group.packageName)
        assertEquals(listOf("new", "old"), group.items.map { it.id })
    }

    @Test
    fun mixedSinglesAndGroups_sortedByLatestTime() {
        val items = listOf(
            Sample("wechat", "com.wechat", 500L),
            Sample("tg-new", "com.telegram", 400L),
            Sample("tg-old", "com.telegram", 300L),
            Sample("mail", "com.mail", 100L),
        )
        val entries = group(items)
        assertEquals(3, entries.size)
        assertEquals("wechat", (entries[0] as NotificationHistoryListEntry.Single).item.id)
        val telegram = entries[1] as NotificationHistoryListEntry.CollapsedGroup
        assertEquals("com.telegram", telegram.group.packageName)
        assertEquals("mail", (entries[2] as NotificationHistoryListEntry.Single).item.id)
    }

    @Test
    fun exactlyOneItem_doesNotCollapse() {
        val items = listOf(Sample("only", "com.only", 100L))
        val entries = group(items)
        assertEquals(1, entries.size)
        assertTrue(entries.single() is NotificationHistoryListEntry.Single)
    }
}
