package com.slideindex.app.ui.navigation

import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavBackStackIntegrationTest {

    private fun stack(initial: AppNavKey = AppNavKey.HomeMain): NavBackStack =
        navBackStackOf(initial)

    @Test
    fun navigate_pushesKeys() {
        val backStack = stack()
        backStack.navigate(AppNavKey.NotificationHub)
        backStack.navigate(AppNavKey.OtpHub)

        assertEquals(AppNavKey.OtpHub, backStack.last())
        assertEquals(3, backStack.size)
    }

    @Test
    fun navigateBackTo_popsUntilTarget() {
        val backStack = stack()
        backStack.navigate(AppNavKey.NotificationHub)
        backStack.navigate(AppNavKey.OtpHub)

        backStack.navigateBackTo(AppNavKey.HomeMain)

        assertEquals(AppNavKey.HomeMain, backStack.last())
        assertEquals(1, backStack.size)
    }

    @Test
    fun extensionFeatureBack_returnsToExtensionHub() {
        val backStack = stack(AppNavKey.ExtensionHub)
        backStack.navigate(AppNavKey.ShellCommands)

        backStack.navigateBackTo(AppNavKey.ExtensionHub)

        assertEquals(AppNavKey.ExtensionHub, backStack.last())
        assertEquals(1, backStack.size)
    }

    @Test
    fun appIndexBack_returnsToExtensionHub() {
        val backStack = stack(AppNavKey.ExtensionHub)
        backStack.navigate(AppNavKey.HomeLayout)

        backStack.navigateBackTo(AppNavKey.ExtensionHub)

        assertEquals(AppNavKey.ExtensionHub, backStack.last())
        assertEquals(1, backStack.size)
    }

    @Test
    fun appIndexHiddenAppsBack_returnsToLayoutThenExtensionHub() {
        val backStack = stack(AppNavKey.ExtensionHub)
        backStack.navigate(AppNavKey.HomeLayout)
        backStack.navigate(AppNavKey.HomeHiddenApps)

        backStack.navigateBackTo(AppNavKey.HomeLayout)
        assertEquals(AppNavKey.HomeLayout, backStack.last())

        backStack.navigateBackTo(AppNavKey.ExtensionHub)
        assertEquals(AppNavKey.ExtensionHub, backStack.last())
        assertEquals(1, backStack.size)
    }

    @Test
    fun replaceRoot_clearsHistory() {
        val backStack = stack()
        backStack.navigate(AppNavKey.ExtensionHub)

        backStack.replaceRoot(AppNavKey.ShakeGestures)

        assertEquals(AppNavKey.ShakeGestures, backStack.last())
        assertEquals(1, backStack.size)
    }

    @Test
    fun isNotificationBranch_coversOtpAndHistoryRoutes() {
        assertTrue(AppNavKey.NotificationHub.isNotificationBranch())
        assertTrue(AppNavKey.OtpRecords(OtpRecordsReturn.Hub).isNotificationBranch())
        assertFalse(AppNavKey.HomeMain.isNotificationBranch())
    }

    @Test
    fun panelSide_roundTripThroughNavSide() {
        assertEquals(PanelSide.RIGHT, "RIGHT".toPanelSide())
        assertEquals("LEFT", PanelSide.LEFT.toNavSide())
    }
}
