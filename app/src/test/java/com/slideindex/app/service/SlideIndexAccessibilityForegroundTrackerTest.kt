package com.slideindex.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideIndexAccessibilityForegroundTrackerTest {

    @Test
    fun computeWindowStatePackageUpdate_ignoresSelfPackage() {
        val result = computeWindowStatePackageUpdate(
            packageName = "com.slideindex.app",
            selfPackageName = "com.slideindex.app",
            prevPackageName = null,
            currPackageName = null,
            hasLaunchIntent = true,
        )

        assertNull(result)
    }

    @Test
    fun computeWindowStatePackageUpdate_foregroundOnlyWhenNoLaunchIntent() {
        val result = computeWindowStatePackageUpdate(
            packageName = "com.example.app",
            selfPackageName = "com.slideindex.app",
            prevPackageName = "com.other.app",
            currPackageName = "com.other.app",
            hasLaunchIntent = false,
        )

        assertTrue(result is WindowStatePackageUpdate.ForegroundOnly)
        assertEquals("com.example.app", (result as WindowStatePackageUpdate.ForegroundOnly).packageName)
    }

    @Test
    fun computeWindowStatePackageUpdate_ignoresExcludedPackage() {
        val result = computeWindowStatePackageUpdate(
            packageName = "com.baidu.input",
            selfPackageName = "com.slideindex.app",
            prevPackageName = "com.wechat",
            currPackageName = "com.wechat",
            hasLaunchIntent = true,
            excludedPackages = setOf("com.baidu.input"),
        )

        assertNull(result)
    }

    @Test
    fun computeWindowStatePackageUpdate_samePackageTriggersOtpCheckOnly() {
        val result = computeWindowStatePackageUpdate(
            packageName = "com.example.app",
            selfPackageName = "com.slideindex.app",
            prevPackageName = "com.other.app",
            currPackageName = "com.example.app",
            hasLaunchIntent = true,
        )

        assertEquals(WindowStatePackageUpdate.SamePackage, result)
    }

    @Test
    fun computeWindowStatePackageUpdate_tracksNewPackageAndSeedsPrevWhenMissing() {
        val result = computeWindowStatePackageUpdate(
            packageName = "com.new.app",
            selfPackageName = "com.slideindex.app",
            prevPackageName = null,
            currPackageName = null,
            hasLaunchIntent = true,
        )

        assertTrue(result is WindowStatePackageUpdate.Tracked)
        val tracked = result as WindowStatePackageUpdate.Tracked
        assertEquals("com.new.app", tracked.currPackageName)
        assertEquals("com.new.app", tracked.prevPackageName)
    }

    @Test
    fun computeWindowStatePackageUpdate_advancesPrevFromCurrent() {
        val result = computeWindowStatePackageUpdate(
            packageName = "com.new.app",
            selfPackageName = "com.slideindex.app",
            prevPackageName = "com.old.app",
            currPackageName = "com.current.app",
            hasLaunchIntent = true,
        )

        assertTrue(result is WindowStatePackageUpdate.Tracked)
        val tracked = result as WindowStatePackageUpdate.Tracked
        assertEquals("com.current.app", tracked.prevPackageName)
        assertEquals("com.new.app", tracked.currPackageName)
    }

    @Test
    fun computeLaunchPreviousAppPlan_launchesCurrentWhenActiveMismatch() {
        val plan = computeLaunchPreviousAppPlan(
            prevPackageName = "com.prev.app",
            currPackageName = "com.curr.app",
            activePackageName = "com.other.app",
        )

        assertTrue(plan is LaunchPreviousAppPlan.LaunchCurrent)
        assertEquals("com.curr.app", (plan as LaunchPreviousAppPlan.LaunchCurrent).packageName)
    }

    @Test
    fun computeLaunchPreviousAppPlan_swapsToPreviousWhenActiveMatchesCurrent() {
        val plan = computeLaunchPreviousAppPlan(
            prevPackageName = "com.prev.app",
            currPackageName = "com.curr.app",
            activePackageName = "com.curr.app",
        )

        assertTrue(plan is LaunchPreviousAppPlan.SwapToPrevious)
        val swap = plan as LaunchPreviousAppPlan.SwapToPrevious
        assertEquals("com.prev.app", swap.targetPackage)
        assertEquals("com.curr.app", swap.newPrevPackageName)
        assertEquals("com.prev.app", swap.newCurrPackageName)
    }

    @Test
    fun computeLaunchPreviousAppPlan_returnsNullWhenPrevEqualsCurr() {
        val plan = computeLaunchPreviousAppPlan(
            prevPackageName = "com.same.app",
            currPackageName = "com.same.app",
            activePackageName = "com.same.app",
        )

        assertNull(plan)
    }

    @Test
    fun computeLaunchPreviousAppPlan_skipsExcludedPrevious() {
        val plan = computeLaunchPreviousAppPlan(
            prevPackageName = "com.baidu.input",
            currPackageName = "com.wechat",
            activePackageName = "com.wechat",
            excludedPackages = setOf("com.baidu.input"),
        )

        assertNull(plan)
    }

    @Test
    fun computeLaunchPreviousAppPlan_skipsExcludedCurrent() {
        val plan = computeLaunchPreviousAppPlan(
            prevPackageName = "com.wechat",
            currPackageName = "com.baidu.input",
            activePackageName = "com.wechat",
            excludedPackages = setOf("com.baidu.input"),
        )

        assertNull(plan)
    }
}
