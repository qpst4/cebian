package com.slideindex.app.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MessageAppFilterCodecTest {

    @Test
    fun encodeDecode_roundTrip_preservesRule() {
        val original = MessageAppFilterRule(
            packageName = "com.chat.app",
            mode = MessageFilterMode.ONLY_MATCHING,
            onlyMatchingConditions = listOf(
                MessageMatchCondition(
                    field = MessageMatchField.TITLE,
                    type = MessageMatchType.CONTAINS,
                    keyword = "验证码",
                ),
            ),
            blockMatchingConditions = listOf(
                MessageMatchCondition(
                    field = MessageMatchField.CONTENT,
                    type = MessageMatchType.REGEX,
                    keyword = "广告.*",
                ),
            ),
        )

        val encoded = MessageAppFilterCodec.encode(original)
        val decoded = MessageAppFilterCodec.decode(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun encodeAll_decodeAll_roundTrip() {
        val rules = listOf(
            MessageAppFilterRule(
                packageName = "com.a",
                mode = MessageFilterMode.BLOCK_MATCHING,
                blockMatchingConditions = listOf(
                    MessageMatchCondition(keyword = "spam"),
                ),
            ),
            MessageAppFilterRule.default("com.b"),
        )

        val encoded = MessageAppFilterCodec.encodeAll(rules)
        val decoded = MessageAppFilterCodec.decodeAll(encoded)

        assertEquals(1, encoded.size)
        assertEquals(rules.first(), decoded["com.a"])
        assertNull(decoded["com.b"])
    }

    @Test
    fun decode_invalidJson_returnsNull() {
        assertNull(MessageAppFilterCodec.decode("{not-json"))
    }
}
