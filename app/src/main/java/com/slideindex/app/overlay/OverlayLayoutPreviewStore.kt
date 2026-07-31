package com.slideindex.app.overlay

import com.slideindex.app.gesture.TriggerHandleDesign

/**
 * 布局/触钮设置页拖动滑条时的临时预览值；不落盘，结束预览后应清空。
 */
object OverlayLayoutPreviewStore {
    @Volatile
    var indexHeightFraction: Float? = null

    data class TriggerHandlePreview(
        val side: PanelSide,
        val handleId: String,
        val edgeWidthDp: Float? = null,
        val topFraction: Float? = null,
        val bottomFraction: Float? = null,
        val shortSwipeDistanceDp: Float? = null,
        val longSwipeDistanceDp: Float? = null,
        val design: TriggerHandleDesign? = null,
    )

    @Volatile
    var triggerHandlePreview: TriggerHandlePreview? = null

    fun clear() {
        indexHeightFraction = null
        triggerHandlePreview = null
    }

    fun clearIndexHeightPreview() {
        indexHeightFraction = null
    }

    fun clearTriggerHandlePreview() {
        triggerHandlePreview = null
    }

    fun mergeTriggerHandlePreview(
        side: PanelSide,
        handleId: String,
        edgeWidthDp: Float? = null,
        topFraction: Float? = null,
        bottomFraction: Float? = null,
        shortSwipeDistanceDp: Float? = null,
        longSwipeDistanceDp: Float? = null,
        design: TriggerHandleDesign? = null,
    ) {
        val existing = triggerHandlePreview?.takeIf { it.side == side && it.handleId == handleId }
        triggerHandlePreview = TriggerHandlePreview(
            side = side,
            handleId = handleId,
            edgeWidthDp = edgeWidthDp ?: existing?.edgeWidthDp,
            topFraction = topFraction ?: existing?.topFraction,
            bottomFraction = bottomFraction ?: existing?.bottomFraction,
            shortSwipeDistanceDp = shortSwipeDistanceDp ?: existing?.shortSwipeDistanceDp,
            longSwipeDistanceDp = longSwipeDistanceDp ?: existing?.longSwipeDistanceDp,
            design = design ?: existing?.design,
        )
    }
}
