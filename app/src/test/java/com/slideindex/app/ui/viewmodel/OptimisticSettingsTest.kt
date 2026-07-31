package com.slideindex.app.ui.viewmodel

import android.os.Build
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardMonitoringMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class OptimisticSettingsTest {
    @Test
    fun mergeUsesRepositoryWhenNoOptimisticTransform() {
        val repository = AppSettings(clipboardBackgroundMonitoring = true)

        val merged = mergeOptimisticSettings(repository, optimisticTransform = null)

        assertEquals(repository, merged)
    }

    @Test
    fun mergeAppliesOptimisticTransform() {
        val repository = AppSettings(
            clipboardBackgroundMonitoringMode = ClipboardMonitoringMode.SHIZUKU_LOGS,
        )

        val merged = mergeOptimisticSettings(repository) {
            it.copy(clipboardBackgroundMonitoringMode = ClipboardMonitoringMode.ROOT_LOGS)
        }

        assertEquals(ClipboardMonitoringMode.ROOT_LOGS, merged.clipboardBackgroundMonitoringMode)
        assertEquals(ClipboardMonitoringMode.SHIZUKU_LOGS, repository.clipboardBackgroundMonitoringMode)
    }
}
