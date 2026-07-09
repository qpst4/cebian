package com.slideindex.app.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class OtpRulesParserTest {

    @Test
    fun parseRules_readsOfficialRuleFields() {
        val json = """
            {
              "version": 1,
              "rules": [
                {
                  "id": "hsbc-china",
                  "name": "汇丰银行中国",
                  "keyword": "验证码",
                  "regex": "验证码[：:\\s为]?(\\d{4,8})"
                },
                {
                  "id": "scoped",
                  "name": "Scoped",
                  "keyword": "OTP",
                  "regex": "(\\d{6})",
                  "packageName": "com.bank.app"
                }
              ]
            }
        """.trimIndent()

        val rules = OtpRulesParser.parseRules(json)

        assertEquals(2, rules.size)
        assertEquals("hsbc-china", rules[0].id)
        assertEquals("汇丰银行中国", rules[0].name)
        assertEquals("验证码", rules[0].keyword)
        assertTrue(rules[0].isOfficial)
        assertEquals("com.bank.app", rules[1].packageName)
    }

    @Test
    fun parseRules_skipsIncompleteEntries() {
        val json = """{"rules":[{"id":"","name":"x","keyword":"k","regex":"r"}]}"""
        assertTrue(OtpRulesParser.parseRules(json).isEmpty())
    }

    @Test
    fun parseRules_assetFixture_matchesExpectedCount() {
        val fixture = javaClass.classLoader!!
            .getResourceAsStream("smscode-rules-fixture.json")!!
            .bufferedReader()
            .use { it.readText() }

        val rules = OtpRulesParser.parseRules(fixture)

        assertEquals(2, rules.size)
        assertEquals("generic-cn-verification", rules[1].id)
    }
}
