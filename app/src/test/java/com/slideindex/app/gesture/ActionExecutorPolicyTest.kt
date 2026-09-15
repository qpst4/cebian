package com.slideindex.app.gesture

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ActionExecutorPolicyTest {

    @Test
    fun shouldLaunchPackageFallback_blankOrMatchingPackage_returnsTrue() {
        assertTrue(ActionExecutorPolicy.shouldLaunchPackageFallback("", "com.example.app"))
        assertTrue(ActionExecutorPolicy.shouldLaunchPackageFallback("  ", "com.example.app"))
        assertTrue(ActionExecutorPolicy.shouldLaunchPackageFallback("com.example.app", "com.example.app"))
    }

    @Test
    fun shouldLaunchPackageFallback_componentIdentifier_returnsFalse() {
        assertFalse(
            ActionExecutorPolicy.shouldLaunchPackageFallback(
                "com.example.app/.MainActivity",
                "com.example.app",
            ),
        )
    }

    @Test
    fun componentFromRawIdentifier_parsesFlatComponent() {
        assertEquals(
            ComponentName("com.example.app", "com.example.app.MainActivity"),
            ActionExecutorPolicy.componentFromRawIdentifier("com.example.app/com.example.app.MainActivity"),
        )
    }

    @Test
    fun componentFromRawIdentifier_expandsRelativeClassName() {
        assertEquals(
            ComponentName("com.example.app", "com.example.app.MainActivity"),
            ActionExecutorPolicy.componentFromRawIdentifier("com.example.app/.MainActivity"),
        )
    }

    @Test
    fun componentFromRawIdentifier_rejectsInvalidInput() {
        assertNull(ActionExecutorPolicy.componentFromRawIdentifier("com.example.app"))
        assertNull(ActionExecutorPolicy.componentFromRawIdentifier(" / "))
    }

    @Test
    fun resolveFreeWindowTargetPackage_skipsSelfAndSystemPackages() {
        assertNull(
            ActionExecutorPolicy.resolveFreeWindowTargetPackage(
                selfPackage = "com.slideindex.app",
                liveForegroundPackage = "com.slideindex.app",
                gestureForegroundPackage = "com.slideindex.app",
                cachedForegroundPackage = "com.android.systemui",
            ),
        )
    }

    @Test
    fun resolveFreeWindowTargetPackage_prefersLiveForegroundPackage() {
        assertEquals(
            "com.live.app",
            ActionExecutorPolicy.resolveFreeWindowTargetPackage(
                selfPackage = "com.slideindex.app",
                liveForegroundPackage = "com.live.app",
                gestureForegroundPackage = "com.example.app",
                cachedForegroundPackage = "com.other.app",
            ),
        )
    }

    @Test
    fun resolveFreeWindowTargetPackage_fallsBackToGestureSnapshot() {
        assertEquals(
            "com.example.app",
            ActionExecutorPolicy.resolveFreeWindowTargetPackage(
                selfPackage = "com.slideindex.app",
                liveForegroundPackage = null,
                gestureForegroundPackage = "com.example.app",
                cachedForegroundPackage = "com.other.app",
            ),
        )
    }
}
