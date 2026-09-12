package com.slideindex.app.floatball

import com.slideindex.app.gesture.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatBallGestureCodecTest {

    @Test
    fun encodeDecode_replacesIneligibleActionWithNone() {
        val encoded = FloatBallGestureCodec.encode(
            FloatBallGestureType.SINGLE_TAP,
            GestureAction.RegionalScreenshotPick,
        )
        val (_, decoded) = FloatBallGestureCodec.decode(encoded)!!
        assertTrue(decoded is GestureAction.None)
    }

    @Test
    fun encodeDecode_roundTrip_preservesEligibleAction() {
        val encoded = FloatBallGestureCodec.encode(
            FloatBallGestureType.SWIPE_DOWN_SHORT,
            GestureAction.Recents,
        )
        val (type, action) = FloatBallGestureCodec.decode(encoded)!!
        assertEquals(FloatBallGestureType.SWIPE_DOWN_SHORT, type)
        assertEquals(GestureAction.Recents, action)
    }
}
