package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregatedImageSearchEnginePreferencesStoreTest {
    @Test
    fun mergeWithCatalog_preservesCustomOrder() {
        val stored = listOf(
            AggregatedImageSearchEngineConfig("Yandex", sortOrder = 0, showInPanel = true),
            AggregatedImageSearchEngineConfig("Google", sortOrder = 1, showInPanel = true),
        )
        val merged = AggregatedImageSearchEnginePreferencesStore.mergeWithCatalog(stored)
        assertEquals(listOf("Yandex", "Google"), merged.take(2).map { it.engineId })
    }

    @Test
    fun panelConfigs_filtersHiddenEngines() {
        val configs = AggregatedImageSearchEngineCatalog.defaultConfigs().map { config ->
            if (config.engineId == "Google") config.copy(showInPanel = false) else config
        }
        val panel = AggregatedImageSearchEnginePreferencesStore.panelConfigs(configs)
        assertTrue(panel.none { it.engineId == "Google" })
        assertTrue(panel.isNotEmpty())
    }

    @Test
    fun preloadConfigs_requiresVisibleAndEnabled() {
        val configs = AggregatedImageSearchEngineCatalog.defaultConfigs().map { config ->
            when (config.engineId) {
                "Google" -> config.copy(preloadOnOpen = false)
                "Yandex" -> config.copy(showInPanel = false)
                else -> config
            }
        }
        val preload = AggregatedImageSearchEnginePreferencesStore.preloadConfigs(configs)
        assertFalse(preload.any { it.engineId == "Google" })
        assertFalse(preload.any { it.engineId == "Yandex" })
    }
}
