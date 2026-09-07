package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AppSettingsGestureExtensionsTest {

    @Test
    fun slotAction_defaultSettings_returnsFactoryDefaultActions() {
        val settings = AppSettings()

        // Fresh install without custom rules should resolve factory defaults, not GestureAction.None
        assertEquals(
            GestureAction.Back,
            settings.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN),
        )
        assertEquals(
            GestureAction.OpenIndex,
            settings.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_UP),
        )
    }

    @Test
    fun slotAction_explicitlyConfiguredNone_returnsNone() {
        val base = AppSettings()
        val settings = base.withSlotAction(
            side = PanelSide.LEFT,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            action = GestureAction.None,
        )

        assertEquals(
            GestureAction.None,
            settings.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN),
        )
    }

    @Test
    fun slotAction_explicitlyConfiguredAction_returnsConfiguredAction() {
        val base = AppSettings()
        val settings = base.withSlotAction(
            side = PanelSide.LEFT,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            action = GestureAction.Screenshot,
        )

        assertEquals(
            GestureAction.Screenshot,
            settings.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN),
        )
    }
}
