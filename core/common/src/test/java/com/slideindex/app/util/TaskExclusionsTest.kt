package com.slideindex.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskExclusionsTest {

    @Test
    fun shouldSkipFreeWindow_skipsSelfAndLaunchers() {
        assertTrue(TaskExclusions.shouldSkipFreeWindow("com.slideindex.app", "com.slideindex.app"))
        assertTrue(TaskExclusions.shouldSkipFreeWindow("com.android.launcher3", "com.slideindex.app"))
    }

    @Test
    fun shouldSkipFreeWindow_allowsRegularApps() {
        assertFalse(TaskExclusions.shouldSkipFreeWindow("com.tencent.mm", "com.slideindex.app"))
    }
}
