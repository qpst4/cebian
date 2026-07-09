package com.slideindex.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class NotificationHistoryCodecTest {

    @Test
    fun encodeDecode_roundTrip_preservesFields() {
        val original = listOf(
            NotificationHistoryItem(
                id = "hist-1",
                packageName = "com.example.messaging",
                title = "Alice",
                text = "Hello",
                postedAtMs = 1_700_000_000_123L,
                intentUri = "intent://open",
                intentParcelBase64 = "parcel",
                intentExtrasBase64 = "extras",
                pendingIntentBase64 = "pending",
                extrasBase64 = "notification-extras",
                notificationKey = "0|com.example|1",
                hidden = true,
                extractedCode = "123456",
                extractionAttempted = true,
            ),
        )

        val decoded = NotificationHistoryCodec.decode(NotificationHistoryCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun decode_blank_returnsEmpty() {
        assertTrue(NotificationHistoryCodec.decode("").isEmpty())
    }

    @Test
    fun decode_skipsEntriesWithoutPackageName() {
        val raw = """[{"id":"x","title":"t","text":"b","postedAtMs":1}]"""
        assertTrue(NotificationHistoryCodec.decode(raw).isEmpty())
    }
}
