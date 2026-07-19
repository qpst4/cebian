package com.slideindex.app.settings

object AggregatedImageSearchEngineCatalog {
    val knownEngineIds: List<String> = listOf(
        "Google",
        "Yandex",
        "TinEye",
        "Iqdb",
        "SauceNao",
        "Iqdb3D",
        "Ascii2d",
        "TraceMoe",
        "AnimeTrace",
        "Copyseeker",
    )

    fun defaultConfigs(): List<AggregatedImageSearchEngineConfig> =
        knownEngineIds.mapIndexed { index, engineId ->
            AggregatedImageSearchEngineConfig(
                engineId = engineId,
                sortOrder = index,
                showInPanel = true,
                preloadOnOpen = true,
            )
        }
}
