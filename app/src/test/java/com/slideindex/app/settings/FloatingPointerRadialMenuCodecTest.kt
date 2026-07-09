package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingPointerRadialMenuCodecTest {

    @Test
    fun encodeDecode_roundTrip_preservesSlots() {
        val slots = FloatingPointerRadialMenuCodec.defaultSlots().toMutableList()
        slots[0] = GestureAction.Screenshot
        slots[5] = GestureAction.Home

        val decoded = FloatingPointerRadialMenuCodec.decode(FloatingPointerRadialMenuCodec.encode(slots))

        assertEquals(GestureAction.Screenshot.type, decoded[0].type)
        assertEquals(GestureAction.Home.type, decoded[5].type)
        assertEquals(FloatingPointerRadialMenuCodec.SLOT_COUNT, decoded.size)
    }

    @Test
    fun decode_empty_returnsDefaults() {
        val decoded = FloatingPointerRadialMenuCodec.decode(emptySet())
        assertEquals(FloatingPointerRadialMenuCodec.defaultSlots(), decoded)
    }

    @Test
    fun encode_replacesFloatingPointerWithNone() {
        val slots = List(FloatingPointerRadialMenuCodec.SLOT_COUNT) { GestureAction.FloatingPointer }
        val decoded = FloatingPointerRadialMenuCodec.decode(FloatingPointerRadialMenuCodec.encode(slots))
        assertEquals(GestureAction.None.type, decoded.first().type)
    }
}
