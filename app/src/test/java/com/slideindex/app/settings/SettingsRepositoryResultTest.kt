package com.slideindex.app.settings

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SettingsRepositoryResultTest {
    @Test
    fun setServiceEnabled_returnsSuccessAndPersists() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = testSettingsRepository(context)

        val result = repository.setServiceEnabled(true)

        assertTrue(result.isSuccess)
        assertTrue(repository.readSnapshot().serviceEnabled)
    }
}
