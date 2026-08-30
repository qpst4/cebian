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
    fun from_voiceSearchAndAssistant_mapCorrectly() {
        assertEquals(GestureAction.VoiceSearch, GestureAction.from(GestureActionType.VOICE_SEARCH, ""))
        assertEquals(GestureAction.VoiceAssistant, GestureAction.from(GestureActionType.VOICE_ASSISTANT, ""))
        assertEquals(GestureActionType.VOICE_SEARCH, GestureActionType.fromId(71))
        assertEquals(GestureActionType.VOICE_ASSISTANT, GestureActionType.fromId(72))
        assertEquals(GestureAction.ToggleAutoRotate, GestureAction.from(GestureActionType.TOGGLE_AUTO_ROTATE, ""))
        assertEquals(GestureAction.ForcePortrait, GestureAction.from(GestureActionType.FORCE_PORTRAIT, ""))
        assertEquals(GestureAction.ForceLandscape, GestureAction.from(GestureActionType.FORCE_LANDSCAPE, ""))
        assertEquals(GestureActionType.TOGGLE_AUTO_ROTATE, GestureActionType.fromId(73))
        assertEquals(GestureActionType.FORCE_PORTRAIT, GestureActionType.fromId(74))
        assertEquals(GestureActionType.FORCE_LANDSCAPE, GestureActionType.fromId(75))
        assertEquals(GestureAction.OpenInternetPanel, GestureAction.from(GestureActionType.OPEN_INTERNET_PANEL, ""))
        assertEquals(GestureAction.OpenVolumePanel, GestureAction.from(GestureActionType.OPEN_VOLUME_PANEL, ""))
        assertEquals(GestureAction.OneHandedMode, GestureAction.from(GestureActionType.ONE_HANDED_MODE, ""))
        assertEquals(GestureAction.CurrentAppInfo, GestureAction.from(GestureActionType.CURRENT_APP_INFO, ""))
        val keyEvent = GestureAction.SimulateKeyEvent(82, "菜单", true)
        assertEquals(keyEvent, GestureAction.from(GestureActionType.SIMULATE_KEY_EVENT, keyEvent.payload))
        assertEquals(GestureActionType.OPEN_INTERNET_PANEL, GestureActionType.fromId(76))
        assertEquals(GestureActionType.OPEN_VOLUME_PANEL, GestureActionType.fromId(77))
        assertEquals(GestureActionType.ONE_HANDED_MODE, GestureActionType.fromId(78))
        assertEquals(GestureActionType.CURRENT_APP_INFO, GestureActionType.fromId(79))
        assertEquals(GestureActionType.SIMULATE_KEY_EVENT, GestureActionType.fromId(80))
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
