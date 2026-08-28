package com.slideindex.app.remind

import org.junit.Assert.assertEquals
import org.junit.Test

class RemindAlarmSchedulerTest {

    @Test
    fun clampMinutes_coercesToSupportedRange() {
        assertEquals(1, RemindAlarmScheduler.clampMinutes(0))
        assertEquals(1, RemindAlarmScheduler.clampMinutes(1))
        assertEquals(120, RemindAlarmScheduler.clampMinutes(120))
        assertEquals(120, RemindAlarmScheduler.clampMinutes(999))
    }

    @Test
    fun presetMinutes_matchesQuickPickerDefaults() {
        assertEquals(listOf(1, 3, 5, 10, 15), RemindAlarmScheduler.PRESET_MINUTES)
    }
}
