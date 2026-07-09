package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AnimationStyleCodecTest {

    @Test
    fun encodeDecode_animationStyles_roundTrip() {
        val original = AnimationStyles(
            type = AnimationStyles.TYPE_WAVE,
            json = """{"backgroundColor":1}""",
            jsonMap = mapOf(
                AnimationStyles.TYPE_WAVE to """{"backgroundColor":1}""",
                AnimationStyles.TYPE_CAPSULE to """{"thickness":8}""",
            ),
        )

        val decoded = AnimationStyleCodec.decode(AnimationStyleCodec.encode(original))

        assertEquals(original.type, decoded.type)
        assertEquals(original.json, decoded.json)
        assertEquals(original.jsonMap, decoded.jsonMap)
    }

    @Test
    fun waveStyle_roundTrip() {
        val original = WaveStyle(
            backgroundColor = 0xFF112233.toInt(),
            strokeColor = 0xFF445566.toInt(),
            strokeWidth = 4,
            width = 120,
            bezierLengthHalfRatio = 0.42f,
            safeBounds = true,
            transformEnabled = false,
            iconColor = 0xFFFFFFFF.toInt(),
            iconScale = 1.25f,
            iconType = WaveStyle.ICON_TYPE_ARROW,
            stickySlideEnabled = true,
            stickySlidePx = 18,
        )

        val decoded = AnimationStyleCodec.decode(AnimationStyleCodec.encodeWave(original), WaveStyle())

        assertEquals(original, decoded)
    }

    @Test
    fun capsuleStyle_roundTrip() {
        val original = CapsuleStyle(
            backgroundColor = 11,
            strokeColor = 22,
            strokeWidth = 3,
            thickness = 9,
            maxLength = 180,
            cornerRadius = 24,
            iconColor = 33,
            iconScale = 0.9f,
            iconType = 2,
        )

        val decoded = AnimationStyleCodec.decode(AnimationStyleCodec.encodeCapsule(original), CapsuleStyle())

        assertEquals(original, decoded)
    }

    @Test
    fun bubbleStyle_roundTrip() {
        val original = BubbleStyle(
            backgroundColor = 44,
            strokeColor = 55,
            strokeWidth = 2,
            diameter = 64,
            maxOffset = 12,
            iconColor = 66,
            iconScale = 1.1f,
            iconType = 1,
        )

        val decoded = AnimationStyleCodec.decode(AnimationStyleCodec.encodeBubble(original), BubbleStyle())

        assertEquals(original, decoded)
    }
}
