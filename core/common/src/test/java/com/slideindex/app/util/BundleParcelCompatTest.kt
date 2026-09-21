package com.slideindex.app.util

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class BundleParcelCompatTest {

    @Test
    @Config(sdk = [31])
    fun getParcelableArrayOfBundles_readsBundleMessages_preTiramisu() {
        val message = Bundle().apply {
            putString("text", "hello")
        }
        val extras = Bundle().apply {
            putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf<Parcelable>(message))
        }

        val messages = BundleParcelCompat.getParcelableArrayOfBundles(extras, Notification.EXTRA_MESSAGES)

        assertNotNull(messages)
        assertEquals(1, messages!!.size)
        assertEquals("hello", messages[0].getString("text"))
    }

    @Test
    @Config(sdk = [33])
    fun getParcelableArrayOfBundles_readsBundleMessages_onTiramisu() {
        val message = Bundle().apply {
            putString("text", "hello")
        }
        val extras = Bundle().apply {
            putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf<Parcelable>(message))
        }

        val messages = BundleParcelCompat.getParcelableArrayOfBundles(extras, Notification.EXTRA_MESSAGES)

        assertNotNull(messages)
        assertEquals(1, messages!!.size)
        assertEquals("hello", messages[0].getString("text"))
    }

    @Test
    @Config(sdk = [33])
    fun getParcelableArrayOfBundles_filtersNonBundleParcelables() {
        val message = Bundle().apply {
            putString("text", "bundle message")
        }
        val extras = Bundle().apply {
            putParcelableArray(
                Notification.EXTRA_MESSAGES,
                arrayOf<Parcelable>(message, Intent(Intent.ACTION_VIEW)),
            )
        }

        val messages = BundleParcelCompat.getParcelableArrayOfBundles(extras, Notification.EXTRA_MESSAGES)

        assertNotNull(messages)
        assertEquals(1, messages!!.size)
        assertEquals("bundle message", messages[0].getString("text"))
    }

    @Test
    @Config(sdk = [33])
    fun getParcelableArrayOfBundles_returnsNullForMissingKey() {
        val extras = Bundle()

        val messages = BundleParcelCompat.getParcelableArrayOfBundles(extras, Notification.EXTRA_MESSAGES)

        assertNull(messages)
    }
}
