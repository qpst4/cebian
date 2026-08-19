package com.slideindex.app.message

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class LastMessageUnlockPolicyTest {

    @Test
    fun shouldOpen_whenEnabledAndPendingMessageExists() {
        assertTrue(
            shouldAutoOpenLastMessageOnUnlock(
                settings = MessageSettings(enabled = true, openLastMessageOnUnlock = true),
                pendingUnlockMessage = sampleData("k1"),
            ),
        )
    }

    @Test
    fun shouldNotOpen_whenDisabled() {
        assertFalse(
            shouldAutoOpenLastMessageOnUnlock(
                settings = MessageSettings(enabled = true, openLastMessageOnUnlock = false),
                pendingUnlockMessage = sampleData("k1"),
            ),
        )
    }

    @Test
    fun shouldNotOpen_whenNoPendingMessage() {
        assertFalse(
            shouldAutoOpenLastMessageOnUnlock(
                settings = MessageSettings(enabled = true, openLastMessageOnUnlock = true),
                pendingUnlockMessage = null,
            ),
        )
    }

    private fun sampleData(key: String): NotificationData = NotificationData(
        packageName = "com.example.app",
        key = key,
        title = "Title",
        content = "Content",
        largeIcon = null,
        appIcon = null,
        contentIntent = null,
        postTime = 1L,
    )
}
