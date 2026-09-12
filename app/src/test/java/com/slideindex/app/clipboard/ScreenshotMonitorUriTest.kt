package com.slideindex.app.clipboard

import android.app.Application
import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ScreenshotMonitorUriTest {
    @Test
    fun rejectsHiddenMediaRootUri() {
        assertFalse(ScreenshotMonitor.isQueryableMediaItemUri(Uri.parse("content://media/external")))
    }

    @Test
    fun rejectsImagesCollectionUriWithoutItemId() {
        assertFalse(
            ScreenshotMonitor.isQueryableMediaItemUri(
                Uri.parse("content://media/external/images/media"),
            ),
        )
    }

    @Test
    fun acceptsConcreteMediaItemUri() {
        assertTrue(
            ScreenshotMonitor.isQueryableMediaItemUri(
                Uri.parse("content://media/external/images/media/42"),
            ),
        )
    }
}
