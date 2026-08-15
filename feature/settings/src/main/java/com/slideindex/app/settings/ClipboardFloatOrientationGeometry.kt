package com.slideindex.app.settings

data class ClipboardFloatOrientationGeometry(
    val panelX: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    val panelY: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    val panelWidthDp: Int = ClipboardFloatWindowMetrics.DEFAULT_WIDTH_DP,
    val panelHeightDp: Int = ClipboardFloatWindowMetrics.DEFAULT_HEIGHT_DP,
    val chipX: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    val chipY: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
)
