package com.slideindex.app.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GestureActionNormalizedTest {

    @Test
    fun from_legacyRemindTypes_mapToRemind() {
        val legacyTypes = listOf(
            GestureActionType.REMIND_1M,
            GestureActionType.REMIND_3M,
            GestureActionType.REMIND_5M,
            GestureActionType.REMIND_10M,
            GestureActionType.REMIND_15M,
        )
        legacyTypes.forEach { type ->
            assertEquals(GestureAction.Remind, GestureAction.from(type, ""))
        }
    }

    @Test
    fun normalized_remind_isIdempotent() {
        assertSame(GestureAction.Remind, GestureAction.Remind.normalized())
    }

    @Test
    fun normalized_otherActions_unchanged() {
        assertSame(GestureAction.Back, GestureAction.Back.normalized())
    }

    @Test
    fun gestureRuleCodec_decodeAll_migratesLegacyRemindActions() {
        val rule = GestureRule.slot(
            side = com.slideindex.app.overlay.PanelSide.LEFT,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            action = GestureAction.Remind5m,
        )
        val encoded = GestureRuleCodec.encode(rule)
        val decoded = GestureRuleCodec.decodeAll(setOf(encoded)).single()
        assertEquals(GestureAction.Remind, decoded.action)
    }
}
