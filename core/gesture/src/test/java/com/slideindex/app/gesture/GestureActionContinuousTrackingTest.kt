package com.slideindex.app.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureActionContinuousTrackingTest {

    @Test
    fun quickLauncherWithPanelId_supportsContinuousOnSwipe() {
        val action = GestureAction.QuickLauncher("panel-1")
        assertTrue(action.isContinuousTrackingKind())
        assertTrue(action.supportsContinuousTracking(GestureTriggerType.SHORT_SWIPE_IN))
        assertFalse(action.supportsContinuousTracking(GestureTriggerType.SHORT_SINGLE_TAP))
    }

    @Test
    fun quickLauncherEmptyPanelId_supportsContinuousOnSwipe() {
        val action = GestureAction.QuickLauncher()
        assertTrue(action.isContinuousTrackingKind())
        assertTrue(action.supportsContinuousTracking(GestureTriggerType.LONG_SWIPE_IN))
    }
}
