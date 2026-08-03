package com.slideindex.app.search.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemSettingsSearchMatcherTest {
    private val entries = listOf(
        entry(title = "WLAN", screenTitle = "网络和连接", keywords = "wifi,无线"),
        entry(title = "蓝牙", screenTitle = "已连接的设备"),
        entry(title = "深色模式", screenTitle = "显示和亮度"),
        entry(title = "应用管理", screenTitle = "应用"),
    )

    @Test
    fun search_matchesTitleKeywordsAndPinyin() {
        val wifiResults = SystemSettingsSearchMatcher.search(entries, "wifi", limit = 3)
        assertEquals("WLAN", wifiResults.first().title)

        val wlanResults = SystemSettingsSearchMatcher.search(entries, "wlan", limit = 3)
        assertTrue(wlanResults.any { it.title == "WLAN" })

        val bluetoothResults = SystemSettingsSearchMatcher.search(entries, "蓝牙", limit = 3)
        assertEquals("蓝牙", bluetoothResults.first().title)
    }

    @Test
    fun search_emptyQueryReturnsEmpty() {
        assertTrue(SystemSettingsSearchMatcher.search(entries, "   ", limit = 3).isEmpty())
    }

    @Test
    fun search_deduplicatesSameEntry() {
        val duplicated = entries + entry(title = "WLAN", screenTitle = "网络和连接", keywords = "wifi")
        val results = SystemSettingsSearchMatcher.search(duplicated, "wifi", limit = 5)
        assertEquals(1, results.count { it.title == "WLAN" })
    }

    private fun entry(
        title: String,
        screenTitle: String? = null,
        keywords: String? = null,
    ) = SystemSettingsSearchEntry(
        title = title,
        screenTitle = screenTitle,
        keywords = keywords,
        packageName = "com.android.settings",
        className = "com.android.settings.SubSettings",
        action = null,
        key = "test_key",
    )
}
