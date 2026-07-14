package com.slideindex.app.settings

import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide

object FloatingPointerDesignIds {
    const val RING = "ring"
}

fun AppSettings.edgeTriggerWidthDp(side: PanelSide): Float = when (side) {
    PanelSide.LEFT -> leftEdgeTriggerWidthDp
    PanelSide.RIGHT -> rightEdgeTriggerWidthDp
}

fun AppSettings.maxEdgeTriggerWidthDp(side: PanelSide): Float {
    val fromHandles = triggerHandles(side).maxOfOrNull { handle ->
        handle.edgeWidthDp.coerceIn(TriggerHandle.MIN_EDGE_WIDTH_DP, TriggerHandle.MAX_EDGE_WIDTH_DP)
    }
    return fromHandles ?: edgeTriggerWidthDp(side)
}

fun AppSettings.triggerHandleEdgeWidthDp(side: PanelSide, handleId: String): Float {
    val handle = triggerHandle(side, handleId) ?: primaryTriggerHandle(side)
    val width = handle.edgeWidthDp
    return if (width > 0f) {
        width.coerceIn(TriggerHandle.MIN_EDGE_WIDTH_DP, TriggerHandle.MAX_EDGE_WIDTH_DP)
    } else {
        edgeTriggerWidthDp(side)
    }
}

fun AppSettings.withResolvedHandleEdgeWidths(): AppSettings {
    fun resolve(handles: List<TriggerHandle>, sideWidth: Float): List<TriggerHandle> =
        handles.map { handle ->
            if (handle.edgeWidthDp > 0f) {
                handle.copy(
                    edgeWidthDp = handle.edgeWidthDp.coerceIn(
                        TriggerHandle.MIN_EDGE_WIDTH_DP,
                        TriggerHandle.MAX_EDGE_WIDTH_DP,
                    ),
                )
            } else {
                handle.copy(
                    edgeWidthDp = sideWidth.coerceIn(
                        TriggerHandle.MIN_EDGE_WIDTH_DP,
                        TriggerHandle.MAX_EDGE_WIDTH_DP,
                    ),
                )
            }
        }
    return copy(
        leftTriggerHandles = resolve(leftTriggerHandles, leftEdgeTriggerWidthDp),
        rightTriggerHandles = resolve(rightTriggerHandles, rightEdgeTriggerWidthDp),
    )
}

fun AppSettings.triggerTopFraction(side: PanelSide): Float =
    primaryTriggerHandle(side).topFraction

fun AppSettings.triggerHeightFraction(side: PanelSide): Float =
    primaryTriggerHandle(side).heightFraction

fun AppSettings.triggerBottomFraction(side: PanelSide): Float =
    primaryTriggerHandle(side).bottomFraction

fun AppSettings.interceptWindowWidthDp(side: PanelSide): Float {
    if (!interceptSystemBackGesture) return maxEdgeTriggerWidthDp(side)
    val triggerWidth = maxEdgeTriggerWidthDp(side)
    val interceptWidth = if (limitMaxInterceptLength) 200f else 320f
    return maxOf(triggerWidth, interceptWidth)
}
