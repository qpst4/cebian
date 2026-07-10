package com.slideindex.app.otp

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class OtpRecordsRepositoryResultTest {

    @Test
    fun delete_returnsSuccessAndRemovesRecord() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val record = OtpRecord(
            id = "record-1",
            code = "123456",
            packageName = "com.test.app",
            title = "Test",
            text = "Your code is 123456",
            timestampMs = 1_700_000_000_000L,
        )
        File(context.filesDir, "otp_records.json").writeText(OtpRecordCodec.encode(listOf(record)))

        val repository = OtpRecordsRepository(context)
        delay(200)

        val result = repository.delete("record-1")

        assertTrue(result.isSuccess)
        assertTrue(repository.records.value.isEmpty())
    }

    @Test
    fun clearAll_returnsSuccessAndEmptiesRecords() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val records = listOf(
            OtpRecord(
                id = "record-1",
                code = "111111",
                packageName = "com.test.app",
                title = "Test 1",
                text = "code 1",
                timestampMs = 1_700_000_000_000L,
            ),
            OtpRecord(
                id = "record-2",
                code = "222222",
                packageName = "com.test.app",
                title = "Test 2",
                text = "code 2",
                timestampMs = 1_700_000_000_001L,
            ),
        )
        File(context.filesDir, "otp_records.json").writeText(OtpRecordCodec.encode(records))

        val repository = OtpRecordsRepository(context)
        delay(200)
        assertEquals(2, repository.records.value.size)

        val result = repository.clearAll()

        assertTrue(result.isSuccess)
        assertTrue(repository.records.value.isEmpty())
    }
}
