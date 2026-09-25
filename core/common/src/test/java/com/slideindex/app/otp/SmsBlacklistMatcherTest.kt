package com.slideindex.app.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SmsBlacklistMatcherTest {
    private val fullRules = SmsBlacklistRuleSet(
        enabled = true,
        actionBlock = true,
        actionDelete = false,
        numbers = listOf("10690000"),
        prefixes = listOf("1069"),
        content = listOf("退订"),
        regex = listOf("""\b\d{6}\b"""),
    )

    @Test
    fun disabledRulesNeverMatch() {
        assertFalse(
            SmsBlacklistMatcher
                .match(fullRules.copy(enabled = false), "10690000", "验证码 123456 退订")
                .matched,
        )
    }

    @Test
    fun matchesExactNumberFirst() {
        val result = SmsBlacklistMatcher.match(fullRules, "10690000", "验证码 123456")
        assertTrue(result.matched)
        assertEquals(SmsBlacklistMatchType.Number, result.type)
        assertEquals("10690000", result.pattern)
    }

    @Test
    fun matchesPrefixWhenNumberMisses() {
        val result = SmsBlacklistMatcher.match(fullRules, "10698888", "验证码 123456")
        assertTrue(result.matched)
        assertEquals(SmsBlacklistMatchType.Prefix, result.type)
    }

    @Test
    fun matchesContentRegardlessOfCase() {
        val result = SmsBlacklistMatcher.match(fullRules, "+8613800000000", "验证码 123456，退订回T")
        assertTrue(result.matched)
        assertEquals(SmsBlacklistMatchType.Content, result.type)
    }

    @Test
    fun matchesRegexOnBody() {
        val rules = fullRules.copy(content = emptyList())
        val result = SmsBlacklistMatcher.match(rules, "+8613800000000", "您的验证码是 123456")
        assertTrue(result.matched)
        assertEquals(SmsBlacklistMatchType.Regex, result.type)
    }

    @Test
    fun carriesConfiguredActions() {
        val result = SmsBlacklistMatcher.match(
            fullRules.copy(actionBlock = false, actionDelete = true),
            "+8613800000000",
            "退订",
        )
        assertTrue(result.matched)
        assertFalse(result.actionBlock)
        assertTrue(result.actionDelete)
    }

    @Test
    fun invalidRegexIsIgnoredInsteadOfThrowing() {
        val rules = SmsBlacklistRuleSet(enabled = true, regex = listOf("([unclosed"))
        assertFalse(SmsBlacklistMatcher.match(rules, "10086", "验证码 123456").matched)
    }

    @Test
    fun emptyBodyAndSenderDoNotMatchBlankPatterns() {
        val rules = SmsBlacklistRuleSet(enabled = true, content = listOf("  "), numbers = listOf(""))
        assertFalse(SmsBlacklistMatcher.match(rules, "", "").matched)
    }

    @Test
    fun codecRoundTripsRuleSet() {
        val decoded = SmsBlacklistRuleSetCodec.decode(SmsBlacklistRuleSetCodec.encode(fullRules))
        assertEquals(fullRules, decoded)
    }

    @Test
    fun textCodecTrimsDropsBlankAndKeepsOrder() {
        assertEquals(
            listOf("1069", "10086"),
            SmsBlacklistTextCodec.parseLines("1069\n\n  10086  \r\n"),
        )
    }
}
