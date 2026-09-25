package com.slideindex.app.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class OtpUserRulesCodecTest {
    private val rules = listOf(
        OtpMatchRule(
            id = "rule-1",
            name = "某银行",
            keyword = "验证码",
            regex = """验证码[：:\s]?(\d{6})""",
            packageName = "com.example.bank",
            enabled = false,
        ),
    )

    @Test
    fun roundTripKeepsFieldsAndForcesUserRule() {
        val decoded = OtpUserRulesCodec.decode(OtpUserRulesCodec.encode(rules))
        assertEquals(1, decoded?.size)
        val rule = decoded!!.first()
        assertEquals("rule-1", rule.id)
        assertEquals("某银行", rule.name)
        assertEquals("com.example.bank", rule.packageName)
        assertFalse(rule.enabled)
        assertFalse(rule.isOfficial)
    }

    @Test
    fun decodeReturnsNullOnInvalidJson() {
        assertNull(OtpUserRulesCodec.decode("not json"))
        assertNull(OtpUserRulesCodec.decode("""{"version":1}"""))
        assertNull(OtpUserRulesCodec.decode(null))
    }

    @Test
    fun decodeSkipsIncompleteRulesAndGeneratesMissingId() {
        val decoded = OtpUserRulesCodec.decode(
            """
            {
              "version": 1,
              "rules": [
                {"name": "缺关键词", "regex": "x"},
                {"name": "可用", "keyword": "验证码", "regex": "\\d{6}"}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(1, decoded?.size)
        assertTrue(decoded!!.first().id.isNotBlank())
    }

    @Test
    fun mergeDeduplicatesByIdAndFingerprint() {
        val existing = listOf(rules.first())
        val merged = OtpUserRulesCodec.merge(
            existing = existing,
            imported = listOf(
                rules.first().copy(id = "rule-1"),
                rules.first().copy(id = "rule-2"),
                rules.first().copy(id = "rule-3", name = "新规则"),
            ),
        )
        assertEquals(2, merged.size)
        assertEquals("新规则", merged.last().name)
    }
}
