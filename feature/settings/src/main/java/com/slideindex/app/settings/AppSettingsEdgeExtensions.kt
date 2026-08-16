package com.slideindex.app.settings

import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide

object FloatingPointerDesignIds {
    const val RING = "ring"
}

fun PanelSide.maxTriggerEdgeWidthDp(): Float = when (this) {
    PanelSide.TOP -> TriggerHandle.MAX_TOP_EDGE_WIDTH_DP
    else -> TriggerHandle.MAX_EDGE_WIDTH_DP
}

fun AppSettings.edgeTriggerWidthDp(side: PanelSide): Float = when (side) {
    PanelSide.LEFT -> leftEdgeTriggerWidthDp
    PanelSide.RIGHT -> rightEdgeTriggerWidthDp
    PanelSide.BOTTOM -> bottomEdgeTriggerWidthDp
    PanelSide.TOP -> topEdgeTriggerWidthDp
}

fun AppSettings.maxEdgeTriggerWidthDp(side: PanelSide): Float {
    val maxWidth = side.maxTriggerEdgeWidthDp()
    val fromHandles = triggerHandles(side).maxOfOrNull { handle ->
        handle.edgeWidthDp.coerceIn(TriggerHandle.MIN_EDGE_WIDTH_DP, maxWidth)
    }
    return fromHandles ?: edgeTriggerWidthDp(side)
}

fun AppSettings.triggerHandleEdgeWidthDp(side: PanelSide, handleId: String): Float {
    val handle = triggerHandle(side, handleId) ?: primaryTriggerHandle(side)
    val maxWidth = side.maxTriggerEdgeWidthDp()
    val width = handle.edgeWidthDp
    return if (width > 0f) {
        width.coerceIn(TriggerHandle.MIN_EDGE_WIDTH_DP, maxWidth)
    } else {
        edgeTriggerWidthDp(side)
    }
}

fun AppSettings.withResolvedHandleEdgeWidths(): AppSettings {
    fun resolve(handles: List<TriggerHandle>, side: PanelSide, sideWidth: Float): List<TriggerHandle> {
        val maxWidth = side.maxTriggerEdgeWidthDp()
        return handles.map { handle ->
            if (handle.edgeWidthDp > 0f) {
                handle.copy(
                    edgeWidthDp = handle.edgeWidthDp.coerceIn(
                        TriggerHandle.MIN_EDGE_WIDTH_DP,
                        maxWidth,
                    ),
                )
            } else {
                handle.copy(
                    edgeWidthDp = sideWidth.coerceIn(
                        TriggerHandle.MIN_EDGE_WIDTH_DP,
                        maxWidth,
                    ),
                )
            }
        }
    }
    return copy(
        edgeTrigger = edgeTrigger.copy(
            leftTriggerHandles = resolve(leftTriggerHandles, PanelSide.LEFT, leftEdgeTriggerWidthDp),
            rightTriggerHandles = resolve(rightTriggerHandles, PanelSide.RIGHT, rightEdgeTriggerWidthDp),
            bottomTriggerHandles = resolve(bottomTriggerHandles, PanelSide.BOTTOM, bottomEdgeTriggerWidthDp),
            topTriggerHandles = resolve(topTriggerHandles, PanelSide.TOP, topEdgeTriggerWidthDp),
        ),
    )
}

fun AppSettings.triggerTopFraction(side: PanelSide): Float =
    primaryTriggerHandle(side).topFraction

fun AppSettings.triggerHeightFraction(side: PanelSide): Float =
    primaryTriggerHandle(side).heightFraction

fun AppSettings.triggerBottomFraction(side: PanelSide): Float =
    primaryTriggerHandle(side).bottomFraction

fun AppSettings.interceptWindowWidthDp(side: PanelSide): Float {
    if (side.isVerticalEdge || !interceptSystemBackGesture) return maxEdgeTriggerWidthDp(side)
    val triggerWidth = maxEdgeTriggerWidthDp(side)
    val interceptWidth = if (limitMaxInterceptLength) 200f else 320f
    return maxOf(triggerWidth, interceptWidth)
}
