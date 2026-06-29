package com.slideindex.app.overlay

import com.slideindex.app.util.ContinuousAdjustController

data class AdjustPanelState(
    val mode: ContinuousAdjustController.Mode,
    var fraction: Float,
    var anchorRawY: Float,
    var dragging: Boolean = false,
)
