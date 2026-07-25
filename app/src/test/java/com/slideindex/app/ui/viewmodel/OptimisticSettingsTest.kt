package com.slideindex.app.ui.viewmodel

import android.os.Build
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardMonitoringPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
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
            clipboardBackgroundMonitoringPath = ClipboardMonitoringPath.LOGCAT,
        )

        val merged = mergeOptimisticSettings(repository) {
            it.copy(clipboardBackgroundMonitoringPath = ClipboardMonitoringPath.LSPOSED)
        }

        assertEquals(ClipboardMonitoringPath.LSPOSED, merged.clipboardBackgroundMonitoringPath)
        assertEquals(ClipboardMonitoringPath.LOGCAT, repository.clipboardBackgroundMonitoringPath)
    }
}
