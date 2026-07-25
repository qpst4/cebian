package com.slideindex.app.ui.settings.clipboard

import com.slideindex.app.settings.ClipboardMonitoringPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardMonitoringUiStateTest {
    @Test
    fun resolveStatusKindByMonitoringPath() {
        assertEquals(
            ClipboardMonitoringStatusKind.READ_LOGS,
            resolveClipboardMonitoringStatusKind(ClipboardMonitoringPath.LOGCAT),
        )
        assertEquals(
            ClipboardMonitoringStatusKind.SELF_HOOK,
            resolveClipboardMonitoringStatusKind(ClipboardMonitoringPath.LSPOSED),
        )
    }

    @Test
    fun selfHookReadyRequiresServiceAndWhitelist() {
        assertTrue(isClipboardSelfHookReady(lsposedServiceConnected = true, lsposedReady = true))
        assertFalse(isClipboardSelfHookReady(lsposedServiceConnected = true, lsposedReady = false))
        assertFalse(isClipboardSelfHookReady(lsposedServiceConnected = false, lsposedReady = true))
    }
}
