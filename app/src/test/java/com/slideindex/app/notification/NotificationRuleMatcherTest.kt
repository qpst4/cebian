package com.slideindex.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRuleMatcherTest {

    @Test
    fun matches_disabledRule_returnsFalse() {
        val rule = sampleRule(enabled = false, appMode = AppMatchMode.INCLUDE, packageName = "com.example.app")
        assertFalse(
            NotificationRuleMatcher.matches(
                rule = rule,
                packageName = "com.example.app",
                channelId = null,
                title = "Hello",
                text = "World",
            ),
        )
    }

    @Test
    fun matches_includeAppMode_matchingPackage_returnsTrue() {
        val rule = sampleRule(
            appMode = AppMatchMode.INCLUDE,
            appTargets = listOf(AppTarget("com.bank.app")),
            textMode = TextMatchMode.ALL,
        )
        assertTrue(
            NotificationRuleMatcher.matches(
                rule = rule,
                packageName = "com.bank.app",
                channelId = null,
                title = "验证码",
                text = "123456",
            ),
        )
    }

    @Test
    fun matches_includeAppMode_wrongPackage_returnsFalse() {
        val rule = sampleRule(
            appMode = AppMatchMode.INCLUDE,
            appTargets = listOf(AppTarget("com.bank.app")),
        )
        assertFalse(
            NotificationRuleMatcher.matches(
                rule = rule,
                packageName = "com.other.app",
                channelId = null,
                title = "验证码",
                text = "123456",
            ),
        )
    }

    @Test
    fun matches_channelIdMismatch_returnsFalse() {
        val rule = sampleRule(channelId = "alerts")
        assertFalse(
            NotificationRuleMatcher.matches(
                rule = rule,
                packageName = "com.example.app",
                channelId = "messages",
                title = "Title",
                text = "Body",
            ),
        )
    }

    @Test
    fun matches_containAnyKeyword_matchesCombinedText() {
        val rule = sampleRule(
            textMode = TextMatchMode.CONTAIN_ANY,
            keywords = listOf("验证码"),
        )
        assertTrue(
            NotificationRuleMatcher.matches(
                rule = rule,
                packageName = "com.example.app",
                channelId = null,
                title = "登录通知",
                text = "您的验证码是 8848",
            ),
        )
    }

    @Test
    fun matches_regexMode_extractsPattern() {
        val rule = sampleRule(
            textMode = TextMatchMode.REGEX,
            regex = "验证码\\d{4,6}",
        )
        assertTrue(
            NotificationRuleMatcher.matches(
                rule = rule,
                packageName = "com.example.app",
                channelId = null,
                title = "",
                text = "验证码884812",
            ),
        )
        assertFalse(
            NotificationRuleMatcher.matches(
                rule = rule,
                packageName = "com.example.app",
                channelId = null,
                title = "",
                text = "没有匹配内容",
            ),
        )
    }

    @Test
    fun findMatching_returnsAllEnabledMatches() {
        val rules = listOf(
            sampleRule(id = "1", name = "hide bank", appMode = AppMatchMode.INCLUDE, appTargets = listOf(AppTarget("com.bank"))),
            sampleRule(id = "2", name = "hide otp", textMode = TextMatchMode.CONTAIN_ANY, keywords = listOf("验证码")),
        )
        val matched = NotificationRuleMatcher.findMatching(
            rules = rules,
            packageName = "com.bank",
            channelId = null,
            title = "验证码",
            text = "123456",
        )
        assertEquals(2, matched.size)
        assertEquals(setOf("1", "2"), matched.map { it.id }.toSet())
    }

    private fun sampleRule(
        id: String = "rule-1",
        name: String = "test",
        enabled: Boolean = true,
        appMode: AppMatchMode = AppMatchMode.ALL,
        appTargets: List<AppTarget> = emptyList(),
        packageName: String = "",
        channelId: String? = null,
        textMode: TextMatchMode = TextMatchMode.ALL,
        keywords: List<String> = emptyList(),
        regex: String? = null,
    ): NotificationFilterRule {
        val legacyPackages = if (appTargets.isNotEmpty()) {
            NotificationFilterRule.formatPackageNames(appTargets.map { it.packageName })
        } else {
            packageName
        }
        return NotificationFilterRule(
            id = id,
            name = name,
            enabled = enabled,
            appMode = appMode,
            appTargets = appTargets,
            packageName = legacyPackages,
            channelId = channelId,
            textMode = textMode,
            keywords = keywords,
            regex = regex,
        )
    }
}
