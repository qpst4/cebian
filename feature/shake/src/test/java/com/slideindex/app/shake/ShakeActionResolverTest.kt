package com.slideindex.app.shake

import com.slideindex.app.gesture.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ShakeActionResolverTest {

    @Test
    fun resolveShakeAction_usesBasicActionByDefault() {
        val shake = ShakeGestureSettings()
        assertEquals(
            GestureAction.Back,
            resolveShakeAction(shake, ShakeGestureType.LEFT_FLIP, "com.example.app", lockScreenActive = false),
        )
    }

    @Test
    fun resolveShakeAction_prefersLockScreenActionsWhenEnabled() {
        val shake = ShakeGestureSettings(
            lockScreenShakeEnabled = true,
            lockScreenActions = mapOf(ShakeGestureType.LEFT_FLIP to GestureAction.Home),
        )
        assertEquals(
            GestureAction.Home,
            resolveShakeAction(shake, ShakeGestureType.LEFT_FLIP, "com.example.app", lockScreenActive = true),
        )
    }

    @Test
    fun resolveShakeAction_skipsLockScreenNoneAndFallsThrough() {
        val shake = ShakeGestureSettings(
            lockScreenShakeEnabled = true,
            lockScreenActions = mapOf(ShakeGestureType.LEFT_FLIP to GestureAction.None),
            independentAppShakeEnabled = true,
            perAppActions = mapOf(
                "com.example.app" to mapOf(ShakeGestureType.LEFT_FLIP to GestureAction.Recents),
            ),
        )
        assertEquals(
            GestureAction.Recents,
            resolveShakeAction(shake, ShakeGestureType.LEFT_FLIP, "com.example.app", lockScreenActive = true),
        )
    }

    @Test
    fun resolveShakeAction_usesPerAppActionWhenIndependentEnabled() {
        val shake = ShakeGestureSettings(
            independentAppShakeEnabled = true,
            perAppActions = mapOf(
                "com.example.app" to mapOf(ShakeGestureType.RIGHT_FLIP to GestureAction.Home),
            ),
        )
        assertEquals(
            GestureAction.Home,
            resolveShakeAction(shake, ShakeGestureType.RIGHT_FLIP, "com.example.app", lockScreenActive = false),
        )
    }

    @Test
    fun resolveShakeAction_ignoresPerAppWhenForegroundMissing() {
        val shake = ShakeGestureSettings(
            independentAppShakeEnabled = true,
            perAppActions = mapOf(
                "com.example.app" to mapOf(ShakeGestureType.RIGHT_FLIP to GestureAction.Home),
            ),
        )
        assertEquals(
            GestureAction.Recents,
            resolveShakeAction(shake, ShakeGestureType.RIGHT_FLIP, foregroundPackage = null, lockScreenActive = false),
        )
    }
}
