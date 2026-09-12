package com.slideindex.app.shake

import com.slideindex.app.gesture.GestureAction
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeGestureCodecSanitizeTest {

    @Test
    fun decodeAction_sanitizesContinuousOnlyAction() {
        val encoded = ShakeGestureCodec.encodeAction(
            ShakeGestureType.LEFT_FLIP,
            GestureAction.RegionalScreenshotPick,
        )
        val (_, action) = ShakeGestureCodec.decodeAction(encoded)!!
        assertTrue(action is GestureAction.None)
    }
}
