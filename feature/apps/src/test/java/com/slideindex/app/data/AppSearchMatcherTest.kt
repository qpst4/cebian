package com.slideindex.app.data

import com.slideindex.app.util.PinyinHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchMatcherTest {

    @Test
    fun search_prefixMatchRanksAbovePackageOnlyMatch() {
        val telegram = app("org.telegram.messenger", "Telegram")
        val packageOnly = app("com.example.text.reader", "AdGuard")
        val apps = listOf(packageOnly, telegram)

        val results = AppSearchMatcher.search(apps, "t", limit = 10)
        assertEquals(telegram, results.first())
        assertTrue(results.contains(packageOnly))
    }

    @Test
    fun search_shortQuerySurfacesTelegramBeforeLimit() {
        val telegram = app("org.telegram.messenger", "Telegram")
        val fillers = (1..12).map { index ->
            app("com.fill.$index", "Zzz$index")
        }
        val packageOnly = (1..8).map { index ->
            app("com.pkg.only.$index", "App$index")
        }
        val apps = fillers + packageOnly + telegram

        val byT = AppSearchMatcher.search(apps, "t", limit = 10)
        assertTrue(byT.contains(telegram))

        val byTe = AppSearchMatcher.search(apps, "te", limit = 10)
        assertTrue(byTe.contains(telegram))

        val byTel = AppSearchMatcher.search(apps, "tel", limit = 10)
        assertEquals(telegram, byTel.first())
    }

    @Test
    fun search_labelPrefixRanksAboveContainsMatch() {
        val prefix = app("com.telescope", "Telescope")
        val contains = app("org.telegram.messenger", "MyTelegram")
        val results = AppSearchMatcher.search(listOf(contains, prefix), "te", limit = 10)
        assertEquals(prefix, results.first())
        assertEquals(contains, results[1])
    }

    @Test
    fun search_emptyQueryReturnsOriginalList() {
        val apps = listOf(app("com.a", "Alpha"))
        assertEquals(apps, AppSearchMatcher.search(apps, "   "))
    }

    @Test
    fun search_pinyinInitialMatchesChineseApps() {
        val weixin = app("com.tencent.mm", "微信")
        val alipay = app("com.eg.android.AlipayGphone", "支付宝")
        val qq = app("com.tencent.mobileqq", "QQ")
        val apps = listOf(weixin, alipay, qq)

        val wxResults = AppSearchMatcher.search(apps, "wx")
        assertEquals(weixin, wxResults.first())

        val zfbResults = AppSearchMatcher.search(apps, "zfb")
        assertEquals(alipay, zfbResults.first())
    }

    private fun app(packageName: String, label: String): AppInfo =
        AppInfo(
            packageName = packageName,
            label = label,
            letter = PinyinHelper.firstLetter(label),
            pinyinKey = PinyinHelper.sortKey(label),
        )
}
