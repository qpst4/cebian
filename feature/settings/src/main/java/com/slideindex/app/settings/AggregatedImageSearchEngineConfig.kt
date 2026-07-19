package com.slideindex.app.settings

import kotlinx.serialization.Serializable

@Serializable
data class AggregatedImageSearchEngineConfig(
    val engineId: String,
    val sortOrder: Int = 0,
    val showInPanel: Boolean = true,
    /** When true, search starts when the panel opens; otherwise only after the user selects the tab. */
    val preloadOnOpen: Boolean = true,
)
