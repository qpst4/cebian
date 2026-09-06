package com.slideindex.app.nativeengine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeEnginePackMigrationNoticeStoreTest {
    @Test
    fun `engine ready only prompts until acknowledged`() {
        assertTrue(
            NativeEnginePackMigrationNoticeStore.evaluateShouldShowMigrationNotice(
                engineAtTargetRevision = true,
                noticeAcknowledged = false,
                hasPendingUpgrade = false,
                hasAwaitingNotice = false,
            ),
        )
        assertFalse(
            NativeEnginePackMigrationNoticeStore.evaluateShouldShowMigrationNotice(
                engineAtTargetRevision = true,
                noticeAcknowledged = true,
                hasPendingUpgrade = false,
                hasAwaitingNotice = false,
            ),
        )
    }

    @Test
    fun `missing engine does not prompt brand new lite users`() {
        assertFalse(
            NativeEnginePackMigrationNoticeStore.evaluateShouldShowMigrationNotice(
                engineAtTargetRevision = false,
                noticeAcknowledged = false,
                hasPendingUpgrade = false,
                hasAwaitingNotice = false,
            ),
        )
    }

    @Test
    fun `missing engine prompts when migration is pending or awaiting`() {
        assertTrue(
            NativeEnginePackMigrationNoticeStore.evaluateShouldShowMigrationNotice(
                engineAtTargetRevision = false,
                noticeAcknowledged = false,
                hasPendingUpgrade = true,
                hasAwaitingNotice = false,
            ),
        )
        assertTrue(
            NativeEnginePackMigrationNoticeStore.evaluateShouldShowMigrationNotice(
                engineAtTargetRevision = false,
                noticeAcknowledged = false,
                hasPendingUpgrade = false,
                hasAwaitingNotice = true,
            ),
        )
    }

    @Test
    fun `missing engine re-prompts after stale acknowledgement`() {
        assertTrue(
            NativeEnginePackMigrationNoticeStore.evaluateShouldShowMigrationNotice(
                engineAtTargetRevision = false,
                noticeAcknowledged = true,
                hasPendingUpgrade = false,
                hasAwaitingNotice = false,
            ),
        )
    }
}
