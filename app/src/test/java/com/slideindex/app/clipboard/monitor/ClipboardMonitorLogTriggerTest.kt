package com.slideindex.app.clipboard.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardMonitorLogTriggerTest {

    @Test
    fun hiddenApiCallback_alwaysTriggers() {
        assertTrue(
            ClipboardMonitorForegroundService.shouldTriggerClipboardRead(null, "com.slideindex.app"),
        )
    }

    @Test
    fun meizuCheckRulesMatch_triggers() {
        assertTrue(
            ClipboardMonitorForegroundService.shouldTriggerClipboardRead(
                "E ClipboardService: checkRulesMatch  match",
                "com.slideindex.app",
            ),
        )
    }

    @Test
    fun otherAppClipboardLog_triggers() {
        assertTrue(
            ClipboardMonitorForegroundService.shouldTriggerClipboardRead(
                "E ClipboardService: setPrimaryClip from com.tencent.mm",
                "com.slideindex.app",
            ),
        )
    }

    @Test
    fun otherAppDenial_triggers() {
        assertTrue(
            ClipboardMonitorForegroundService.shouldTriggerClipboardRead(
                "Denying clipboard access to top.coclyun.clipshare, application is not in focus",
                "com.slideindex.app",
            ),
        )
    }

    @Test
    fun ownAppEcho_doesNotTrigger() {
        assertFalse(
            ClipboardMonitorForegroundService.shouldTriggerClipboardRead(
                "E ClipboardService: setPrimaryClip from com.slideindex.app",
                "com.slideindex.app",
            ),
        )
        assertFalse(
            ClipboardMonitorForegroundService.shouldTriggerClipboardRead(
                "Denying clipboard access to com.slideindex.app, application is not in focus",
                "com.slideindex.app",
            ),
        )
    }
}

