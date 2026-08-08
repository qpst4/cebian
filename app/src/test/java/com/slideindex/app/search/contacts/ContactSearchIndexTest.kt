package com.slideindex.app.search.contacts

import com.slideindex.app.util.PinyinHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSearchIndexTest {

    @Test
    fun testPinyinAndInitialExtraction() {
        val name = "张三"
        val fullPinyin = PinyinHelper.sortKey(name)
        val initialPinyin = PinyinHelper.initialKey(name)

        assertEquals("zhangsan", fullPinyin)
        assertEquals("zs", initialPinyin)
    }

    @Test
    fun testContactSearchScoring() {
        val entry1 = ContactSearchEntry(
            id = 1L,
            name = "张三",
            phoneNumber = "13800001234",
            formattedPhone = "138 0000 1234",
            fullPinyin = PinyinHelper.sortKey("张三"),
            initialPinyin = PinyinHelper.initialKey("张三"),
        )
        val entry2 = ContactSearchEntry(
            id = 2L,
            name = "李四",
            phoneNumber = "13988885678",
            formattedPhone = "139 8888 5678",
            fullPinyin = PinyinHelper.sortKey("李四"),
            initialPinyin = PinyinHelper.initialKey("李四"),
        )

        val entries = listOf(entry1, entry2)

        // 1. Search by initial pinyin "zs" -> expect 张三
        val zsMatch = entries.filter { it.initialPinyin.contains("zs") || it.fullPinyin.contains("zs") || it.name.contains("zs") }
        assertEquals(1, zsMatch.size)
        assertEquals("张三", zsMatch.first().name)

        // 2. Search by phone digits "138" -> expect 张三
        val numMatch = entries.filter { it.phoneNumber.contains("138") }
        assertEquals(1, numMatch.size)
        assertEquals("张三", numMatch.first().name)
    }
}
