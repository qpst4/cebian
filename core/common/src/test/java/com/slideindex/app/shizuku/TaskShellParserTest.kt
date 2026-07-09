package com.slideindex.app.shizuku

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskShellParserTest {
    @Test
    fun parseCmdTaskList_readsTaskHeader() {
        val dump = """
            * TASK 42: com.example.app
              realActivity=ComponentInfo{com.example.app/.MainActivity}
        """.trimIndent()
        val entries = TaskShellParser.parseCmdTaskList(dump)
        assertEquals(1, entries.size)
        assertEquals(42, entries[0].taskId)
        assertEquals("com.example.app", entries[0].packageName)
    }
}