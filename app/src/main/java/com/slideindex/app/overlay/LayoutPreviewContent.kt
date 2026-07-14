package com.slideindex.app.overlay

enum class LayoutPreviewContent {
    TRIGGER_ONLY,
    INDEX_ONLY,
}

data class LayoutPreviewFocus(
    val side: PanelSide,
    val handleId: String,
    val showSwipeDistances: Boolean = false,
)
