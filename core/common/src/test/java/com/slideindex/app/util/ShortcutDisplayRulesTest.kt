package com.slideindex.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutDisplayRulesTest {

    @Test
    fun isDisplayable_rejectsNumericOnlyLabels() {
        assertFalse(ShortcutDisplayRules.isDisplayable("shortcut_1", "12345"))
    }

    @Test
    fun isDisplayable_acceptsHumanReadableChineseLabel() {
        assertTrue(ShortcutDisplayRules.isDisplayable("scan", "扫一扫"))
    }

    @Test
    fun isInternalKey_detectsGeneratedShortcutIds() {
        assertTrue(ShortcutDisplayRules.isInternalKey("shortcut_id_42"))
        assertFalse(ShortcutDisplayRules.isInternalKey("扫一扫"))
    }
}
