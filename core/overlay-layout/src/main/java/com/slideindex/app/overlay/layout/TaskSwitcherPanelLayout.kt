package com.slideindex.app.overlay.layout

import android.graphics.RectF

data class TaskSwitcherRowLayout(
    val entry: TaskSwitcherRowEntry,
    val rowRect: RectF,
    val closeRect: RectF,
    val freeWindowRect: RectF,
)

data class TaskSwitcherPanelLayout(
    val panelRect: RectF,
    val listRect: RectF,
    val rows: List<TaskSwitcherRowLayout>,
    val closeAllRect: RectF,
    val scrollOffset: Float,
    val maxScrollOffset: Float,
    /** Extra list viewport height showing a sliver of the next row when scrollable. */
    val listScrollPeekHeight: Float,
)
