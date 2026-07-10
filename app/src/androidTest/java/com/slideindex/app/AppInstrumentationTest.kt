package com.slideindex.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppInstrumentationTest {

    @Test
    fun targetContext_usesExpectedPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.slideindex.app", context.packageName)
    }

    @Test
    fun application_isSlideIndexApp() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assertTrue(app is SlideIndexApp)
    }

    @Test
    fun mainActivity_launchIntentIsResolvable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull(launchIntent)
        assertEquals(MainActivity::class.java.name, launchIntent!!.component?.className)
    }
}
